package mtech.irs.ekgs.frameworx;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.cache.LoadingCache;

import mtech.irs.ekgs.frameworx.service.FrameworxService;
import mtech.irs.ekgs.model.DialogAction;
import mtech.irs.ekgs.model.SearchInput;
import mtech.irs.ekgs.model.SearchResults;
import mtech.irs.ekgs.util.AppContextUtils;
import mtech.irs.ekgs.util.CacheUtils;
import mtech.irs.ekgs.util.GraphUtils;

/**
 * Static helper methods
 * 
 * @author tanshyi
 */
abstract public class Frameworx {
	
	private static final String[] SEARCH_NODE_LABELS = new String[] {"People", "Process", "Technology"};
	
	private static final Logger logger = LoggerFactory.getLogger(Frameworx.class);
	
	private static FrameworxService service;
	
	private static final LoadingCache<String, List<String>> nodeNamesCache = CacheUtils.buildCache(label -> {
		List<String> names = GraphUtils.findNodePropValues(label, "longName", null, false);
		names.sort((l, r) -> l.compareTo(r));
		return names;
	}, 60);
	
	public static FrameworxService service() {
		if(service == null) {
			service = AppContextUtils.getBean(FrameworxService.class);
		}
		return service;
	}
	
	public static List<String> findNodeNames(String label){
		return nodeNamesCache.getUnchecked(label);
	}
	
	public static List<String> findProcessStreams(){
		return GraphUtils.findRelationPropValues(null, "processStream", ".+", true);
	}
	
	/**
	 * Populate search suggestions with node labels applicable to node scan
	 * @param results
	 * @param prefix
	 */
	public static void searchSuggestionForNodeScan(SearchResults results, String prefix) {
		Stream.of(SEARCH_NODE_LABELS).forEach(l -> {
			results.addSuggestion(prefix + l);
		});
	}
	
	/**
	 * Populate search suggestions for node scan with specific node label
	 * @param results
	 * @param prefix
	 * @param label
	 */
	public static void searchSuggestionForNodeScan(SearchResults results, String prefix, String label) {
		findNodeNames(label).forEach(n -> {
			results.addSuggestion(prefix + label + " " + n);
		});
	}
	
	/**
	 * Populate search result for plotting node scan graph, if input matches one of the node
	 * @param results
	 * @param input
	 */
	public static void searchResultForNodeScan(SearchResults results, SearchInput input) {
		final String value = input.getValue();
		String label = Stream.of(SEARCH_NODE_LABELS)
				.filter(l -> value.contains(l + " "))
				.findFirst()
				.orElse(null);
		if(label != null) {
			String nodeName = value.substring(value.indexOf(label) + label.length()).trim();
			findNodeNames(label).stream()
				.filter(n -> n.equalsIgnoreCase(nodeName))
				.findFirst()
				.ifPresent(n -> {
					results.addAction("view", Map.of(
							"graph", cypherForNodeScan(label, n, 3),
							"description", descForNodeScan(n, 3)));
				});
		}
	}
	
	public static void searchResultForNodeScan(SearchResults results, DialogAction action) {
		final String label = (String) action.getParams().get("nodeLabel");
		final String name = (String) action.getParams().get("nodeName");
		final String depth = (String) action.getParams().get("depth");
		if(StringUtils.hasLength(label) && StringUtils.hasLength(name)) {
			results.addAction("view", Map.of(
					"graph", cypherForNodeScan(label, name, Integer.valueOf(depth)),
					"description", descForNodeScan(name, Integer.valueOf(depth))));
		}
	}
	
	public static String cypherForNodeScan(String label, String name, int depth) {
		String cypher = 
				"MATCH(n:" + label + "{longName:'" + name + "'}) " + 
				"CALL apoc.path.spanningTree(n, {minLevel: 1, maxLevel: " + depth + "}) " + 
				"YIELD path " + 
				"RETURN path";
		logger.debug("Cypher: {}", cypher);
		return cypher;
	}
	
	public static List<String> descForNodeScan(String name, int depth) {
		return Arrays.asList(
				"Please refer to the graph for the 360-degree scan for node (" + name + ") with depth limit " + depth + ".",
				depth < 3 ? "If you wish to view more information about this node, please query again using a higher depth limit ranging from 1 to 3." : "");
	}
	
	public static void searchSuggestionForRelationScan(SearchResults results, String prefix) {
		GraphUtils.findRelationTypesAll().forEach(l -> {
			results.addSuggestion(prefix + l);
		});
	}
	
	public static void searchResultForRelationScan(SearchResults results, SearchInput input) {
		final String value = input.getValue().trim();
		GraphUtils.findRelationTypesAll().stream()
				.filter(r -> value.endsWith(r))
				.findFirst()
				.ifPresent(r -> {
					results.addAction("view", Map.of(
							"graph", cypherForRelationScan(r, 100, false),
							"description", descForRelationScan(r, 100)));
				});
	}
	
	public static void searchResultForRelationScan(SearchResults results, DialogAction action) {
		final String relationship = (String) action.getParams().get("relationship");
		final String limit = (String) action.getParams().get("limit");
		if(StringUtils.hasLength(relationship)) {
			results.addAction("view", Map.of(
					"graph", cypherForRelationScan(relationship, Integer.parseInt(limit), false),
					"description", descForRelationScan(relationship, Integer.parseInt(limit))));
		}
	}
	
	public static String cypherForRelationScan(String type, int limit, boolean count) {
		String cypher = "MATCH (n)-[r:" + type + "]->(m) RETURN " + (count? "count(*) as count": "* LIMIT " + limit);
		logger.debug("Cypher: {}", cypher);
		return cypher;
	}
	
	public static List<String> descForRelationScan(String type, int limit) {
		String countCypher = cypherForRelationScan(type, limit, true);
		long count = GraphUtils.service().query(countCypher, null, "count", Long.class).findFirst().orElse(0l);
		return Arrays.asList(
				"Please refer to the graph for the nodes that are linked with the relationship <" + type + ">.", 
				"There are " + count + " instances of this relationship in total.",
				count > limit? " The graph only display the first " + limit + " instances.": "");
	}
	
	public static void searchSuggestionForProcessStreamScan(SearchResults results, String prefix) {
		findProcessStreams().forEach(l -> {
			results.addSuggestion(prefix + l);
		});
	}
	
	public static void searchResultForProcessStreamScan(SearchResults results, SearchInput input) {
		final String value = input.getValue();
		String streamName = findProcessStreams().stream()
				.filter(l -> value.contains(l))
				.findFirst()
				.orElse(null);
		if(streamName != null) {
			results.addAction("view", Map.of(
					"graph", cypherForProcessStreamScan(streamName),
					"description", descForProcessStreamScan(streamName)));
		}
	}
	
	public static void searchResultForProcessStreamScan(SearchResults results, DialogAction action) {
		final String streamName = (String) action.getParams().get("processStream");
		if(StringUtils.hasLength(streamName)) {
			results.addAction("view", Map.of(
					"graph", cypherForProcessStreamScan(streamName),
					"description", descForProcessStreamScan(streamName)));
		}
	}
	
	public static String cypherForProcessStreamScan(String streamName) {
		String cypher = "MATCH p=()-[r]->() WHERE trim(r.processStream) = '"+ streamName +"' RETURN p";
		logger.debug("Cypher: {}", cypher);
		return cypher;
	}
	
	public static List<String> descForProcessStreamScan(String streamName) {
		return Arrays.asList(
				"Please refer to the graph for the end-to-end business process group " + streamName + ".",
				"If you wish to view certain sub-process in this graph, please use Relationship Scan for that specific sub process within this process group.");
	}
	
	public static void searchResultForRecommendSolution(SearchResults results, DialogAction action) {
		final String solutionType = (String) action.getParams().get("solutionType");
		final String solutionTarget = (String) action.getParams().get("solutionTarget");
		if(StringUtils.hasLength(solutionType)) {
			String weightProperty = null;
			if(solutionTarget.contains("time")) {
				weightProperty = "relationTime";
			}else if(solutionTarget.contains("cost")) {
				weightProperty = "relationCost";
			}else {
				return;
			}
			results.addAction("view", Map.of(
					"graph", cypherForShortestPath(weightProperty, false),
					"table", tableInfoForShortestPath(weightProperty),
					"description", descForShortestPath()));
		}
	}
	
	public static void searchResultForShortestPath(SearchResults results, SearchInput input) {
		final String value = input.getValue();
		String weightProperty = null;
		if(value.contains("time")) {
			weightProperty = "relationTime";
		}else if(value.contains("cost")) {
			weightProperty = "relationCost";
		}else {
			return;
		}
		results.addAction("view", Map.of(
				"graph", cypherForShortestPath(weightProperty, false),
				"table", tableInfoForShortestPath(weightProperty),
				"description", descForShortestPath()));
	}
	
	public static String cypherForShortestPath(String weightProperty, boolean table) {
		String cypher = 
				"MATCH (start {name:'Customer1'}), (end {name:'CIR1'}) " + 
				"CALL algo.shortestPath.stream(start, end, '" + weightProperty + "',{direction:'OUTGOING'}) " + 
				"YIELD nodeId, cost ";
		if(table) {
			cypher +=
				"WITH nodeId, cost, algo.asNode(nodeId) as n " +
				"RETURN nodeId as id, cost, labels(n)[0] as label, n.name as name, n.longName as longName, n.shortDescription as desc";
		}else {
			cypher +=
				"WITH collect(nodeId) as ids, collect(cost) as weights " +
				"CALL algo.asPath(ids, weights) YIELD path " +
				"RETURN path";
		}
		logger.debug("Cypher: {}", cypher);
		return cypher;
	}
	
	public static Map<String, Object> tableInfoForShortestPath(String weightProperty){
		List<Map<String, Object>> data = GraphUtils.service().query(cypherForShortestPath(weightProperty, true), null);
		String cost = String.format("%.2f", data.get(data.size() - 1).get("cost"));
		
		List<String> description = Arrays.asList(
				"The following steps can fulfil the Request-to-Answer process with the "
				+ ("relationTime".equals(weightProperty)? "shortest response time ": "lowest cost ") + cost,
				"Please refer to below table for the step cost summary");
		
		return Map.of(
				"columns", Arrays.asList(
						Map.of("name", "id", "label", "Node ID"),
						Map.of("name", "label", "label", "Node Type"),
						Map.of("name", "name", "label", "Name"),
						Map.of("name", "longName", "label", "Long Name"),
						Map.of("name", "cost", "label", "Step Cost", "align", "right", "dp", 2),
						Map.of("name", "desc", "label", "Step Description")),
				"rows", data,
				"description", description);
	}
	
	public static List<String> descForShortestPath(){
		return Arrays.asList("The following graph shows the end-to-end graph view about the optimized process flow.");
	}
}
