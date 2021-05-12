package org.aksw.simba.lsq.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.aksw.commons.util.strings.StringUtils;
import org.aksw.jena_sparql_api.utils.ExprUtils;
import org.aksw.simba.lsq.vocab.LSQ;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.PathBlock;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprFunction;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementAssign;
import org.apache.jena.sparql.syntax.ElementBind;
import org.apache.jena.sparql.syntax.ElementData;
import org.apache.jena.sparql.syntax.ElementDataset;
import org.apache.jena.sparql.syntax.ElementExists;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementMinus;
import org.apache.jena.sparql.syntax.ElementNamedGraph;
import org.apache.jena.sparql.syntax.ElementNotExists;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementService;
import org.apache.jena.sparql.syntax.ElementSubQuery;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.jena.sparql.syntax.ElementUnion;
import org.apache.jena.sparql.syntax.ElementVisitor;
import org.apache.jena.sparql.syntax.ElementWalker;
import org.apache.jena.sparql.util.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to extract a set of features (expressed in SPIN terms) from a
 * SPARQL element
 *
 * @author raven
 *
 */
public class ElementVisitorFeatureExtractor
    implements ElementVisitor
{
    private static final Logger logger = LoggerFactory.getLogger(ElementVisitorFeatureExtractor.class);

    protected Map<Resource, Integer> featureToFrequency = new LinkedHashMap<>();

    // Note: A query without triples and only with a values clause is perfectly fine and could be
    // generated by certain caching systems - hence it makes sense to track even this basic feature.
    public void emit(BasicPattern bgp) {
        // TODO We could emit a triple indicating the presence of a BasicPattern
        // but because they should not be empty anyway, its sufficient to only
        // indicate only the presence of triple patterns
        //outFeatures.add(LSQ.BasicPattern);
//        if(!bgp.isEmpty()) {
//            outFeatures.add(LSQ.TriplePattern);
//        }
        for (@SuppressWarnings("unused") Triple item : bgp.getList()) {
            incrementFeatureCount(LSQ.TriplePattern);
        }
    }


    public int incrementFeatureCount(Resource feature) {
        int newCount = addAndGet(featureToFrequency, feature, 1);
        return newCount;
    }



    public static <K> int addAndGet(Map<K, Integer> map, K key) {
        int newCount = addAndGet(map, key, 1);
        return newCount;
    }


    public static <K> int addAndGet(Map<K, Integer> map, K key, int delta) {
        int newCount = map.compute(key, (k, v) -> ((v == null ? 0 : v) + delta));

        return newCount;
    }


    @Override
    public void visit(ElementTriplesBlock el) {
        emit(el.getPattern());
    }

    @Override
    public void visit(ElementAssign el) {
        incrementFeatureCount(LSQ.Assign);
    }

    @Override
    public void visit(ElementGroup el) {
        incrementFeatureCount(LSQ.Group);
    }

    @Override
    public void visit(ElementDataset el) {
        incrementFeatureCount(LSQ.Dataset);
    }

    @Override
    public void visit(ElementData el) {
        incrementFeatureCount(LSQ.Values);
    }

    @Override
    public void visit(ElementUnion el) {
        incrementFeatureCount(LSQ.Union);
    }

    @Override
    public void visit(ElementOptional el) {
        incrementFeatureCount(LSQ.Optional);
    }

    @Override
    public void visit(ElementFilter el) {
        incrementFeatureCount(LSQ.Filter);

        Expr baseExpr = el.getExpr();
        List<Expr> exprs = ExprUtils.linearizePrefix(baseExpr, Collections.emptySet()).collect(Collectors.toList());

        for(Expr expr : exprs) {
            incrementFeatureCount(LSQ.Functions);

            if(expr.isFunction()) {
                // TODO Will use full URIs for custom sparql functions - may want to shorten them with prefixes
                ExprFunction fn = expr.getFunction();

                Symbol symbol = fn.getFunctionSymbol();
                String fnName = null;
                fnName = fnName != null ? fnName : symbol.getSymbol();
                fnName = fnName != null ? fnName : fn.getFunctionIRI();
                fnName = fnName != null ? fnName : fn.getOpName();

                if(fnName != null) {
                    fnName = StringUtils.urlEncode(fnName);
                    Resource fnRes = ResourceFactory.createResource(LSQ.NS + "fn-" + fnName);
                    incrementFeatureCount(fnRes);
                } else {
                    logger.warn("Could not obtain any of {label/symbol/iri} for "+ expr);
                }
            }
        }
    }

    @Override
    public void visit(ElementBind el) {
        incrementFeatureCount(LSQ.Bind);
    }

    @Override
    public void visit(ElementService el) {
        incrementFeatureCount(LSQ.Service);
    }

    @Override
    public void visit(ElementExists el) {
        incrementFeatureCount(LSQ.Exists);
    }

    @Override
    public void visit(ElementNotExists el) {
        incrementFeatureCount(LSQ.NotExists);
    }

    @Override
    public void visit(ElementMinus el) {
        incrementFeatureCount(LSQ.Minus);
    }

    @Override
    public void visit(ElementNamedGraph el) {
        incrementFeatureCount(LSQ.NamedGraph);
    }

    /**
     * Note: Paths
     *
     */
    @Override
    public void visit(ElementPathBlock el) {
        PathBlock pathBlock = el.getPattern();
        for(TriplePath item : pathBlock.getList()) {
            Triple t = item.asTriple();
            if(t != null) {
                // Do not expose a path that can be expressed as a triple (pattern) as a TriplePath
                incrementFeatureCount(LSQ.TriplePattern);
            } else {
                incrementFeatureCount(LSQ.TriplePath);
                Path path = item.getPath();

                Map<Resource, Integer> pathFeatures = PathVisitorFeatureExtractor.getFeatures(path);
                for (Entry<Resource, Integer> e : pathFeatures.entrySet()) {
                    addAndGet(featureToFrequency, e.getKey(), e.getValue());
                }
            }
        }


        // Old code ; subject to removal
//        Op op = PathLib.pathToTriples(el.getPattern());
//        if(op instanceof OpBGP) {
//            emit(features, ((OpBGP) op).getPattern());
//        } else if (op instanceof OpPath) {
//            OpPath opPath = (OpPath)op;
//            Path path = opPath.getTriplePath().getPath();
//
//            features.add(LSQ.TriplePath);
//
//            Set<Resource> pathFeatures = PathVisitorFeatureExtractor.getFeatures(path);
//            features.addAll(pathFeatures);
//
//
//        } else {
//            throw new RuntimeException("Unsupported algebra expression: " + op);
//        }
    }

    @Override
    public void visit(ElementSubQuery el) {
        incrementFeatureCount(LSQ.SubQuery);

        Element subEl = el.getQuery().getQueryPattern();

        ElementWalker.walk(subEl, this);
    }


    public Map<Resource, Integer> getFeatures() {
        return this.featureToFrequency;
    }

    public static Map<Resource, Integer> getFeatures(Query query) {
        Map<Resource, Integer> result = new LinkedHashMap<>();

        Map<Resource, Integer> elFeatures = getFeatures(query.getQueryPattern());
        result.putAll(elFeatures);

        switch(query.getQueryType()) {
        case Query.QueryTypeSelect: addAndGet(result, LSQ.Select); break;
        case Query.QueryTypeConstruct: addAndGet(result, LSQ.Construct); break;
        case Query.QueryTypeDescribe: addAndGet(result, LSQ.Describe); break;
        case Query.QueryTypeAsk: addAndGet(result, LSQ.Ask); break;
        default: addAndGet(result, LSQ.Unknown); break;
        }

        if(query.isDistinct()) {
            addAndGet(result, LSQ.Distinct);
        }

        if(query.isReduced()) {
            addAndGet(result, LSQ.Reduced);
        }

        if(query.hasOrderBy()) {
            addAndGet(result, LSQ.OrderBy);
        }

        if(query.hasGroupBy()) {
            addAndGet(result, LSQ.GroupBy);
        }

        if(query.getLimit() != Query.NOLIMIT) {
            addAndGet(result, LSQ.Limit);
        }

        if(query.getOffset() != Query.NOLIMIT && query.getOffset() != 0) {
            addAndGet(result, LSQ.Offset);
        }

        if(query.hasAggregators()) {
            addAndGet(result, LSQ.Aggregators);


            List<ExprAggregator> aggs = query.getAggregators();
            for(ExprAggregator agg : aggs) {
                Resource fnRes = ResourceFactory.createResource(LSQ.NS + "agg-" + agg.getAggregator().getName().toLowerCase());
                addAndGet(result, fnRes);
            }

        }


        return result;
    }

    public static Map<Resource, Integer> getFeatures(Element element) {
        Map<Resource, Integer> result;

        if(element != null)
        {
            ElementVisitorFeatureExtractor visitor = new ElementVisitorFeatureExtractor();
            ElementWalker.walk(element, visitor);
            result = visitor.getFeatures();
        } else {
            result = Collections.emptyMap();
        }
        return result;
    }
//    @Override
//    public void visit(ElementFind el) {
//        incrementFeatureCount(LSQ.Find);
//    }
}
