// Phase 13 pilot/enterprise graph intelligence queries.
// Load taxpayer and relationship data from PostgreSQL graph_edges exports before running these.

CREATE CONSTRAINT taxpayer_id IF NOT EXISTS
FOR (taxpayer:Taxpayer)
REQUIRE taxpayer.id IS UNIQUE;

CREATE CONSTRAINT graph_node_key IF NOT EXISTS
FOR (node:GraphNode)
REQUIRE (node.nodeType, node.id) IS UNIQUE;

CREATE INDEX graph_edge_type IF NOT EXISTS
FOR ()-[edge:RELATED_TO]-()
ON (edge.edgeType);

CREATE INDEX taxpayer_risk_score IF NOT EXISTS
FOR (taxpayer:Taxpayer)
ON (taxpayer.riskScore);

// Parameterized edge upsert for rows exported from PostgreSQL graph_edges.
MERGE (source:GraphNode {nodeType: $sourceType, id: $sourceId})
SET source.label = $sourceLabel,
    source.riskScore = coalesce($sourceRiskScore, source.riskScore, 0)
MERGE (target:GraphNode {nodeType: $targetType, id: $targetId})
SET target.label = $targetLabel,
    target.riskScore = coalesce($targetRiskScore, target.riskScore, 0)
MERGE (source)-[edge:RELATED_TO {
  edgeType: $edgeType,
  source: $sourceSystem
}]->(target)
SET edge.weight = $weight,
    edge.evidence = $evidence,
    edge.updatedAt = datetime();

// Invoice rings: taxpayer cycles with repeated invoice-trade edges.
MATCH cycle = (start:GraphNode {nodeType: "TAXPAYER"})-[:RELATED_TO*2..4]->(start)
WHERE all(edge IN relationships(cycle) WHERE edge.edgeType = "INVOICE_TRADE")
RETURN start.id AS taxpayerId,
       start.label AS taxpayerName,
       length(cycle) AS cycleLength,
       reduce(total = 0.0, edge IN relationships(cycle) | total + edge.weight) AS cycleWeight
ORDER BY cycleWeight DESC
LIMIT 50;

// Shared identifiers and related entities.
MATCH (left:GraphNode {nodeType: "TAXPAYER"})-[edge:RELATED_TO]-(right:GraphNode {nodeType: "TAXPAYER"})
WHERE edge.edgeType IN ["SHARED_REGISTRATION_NUMBER", "DIRECTOR", "BENEFICIAL_OWNER"]
RETURN left.id AS sourceTaxpayerId,
       left.label AS sourceTaxpayer,
       right.id AS targetTaxpayerId,
       right.label AS targetTaxpayer,
       edge.edgeType AS relationshipType,
       edge.weight AS confidence
ORDER BY confidence DESC
LIMIT 100;

// High-risk clusters for officer triage.
MATCH (left:GraphNode {nodeType: "TAXPAYER"})-[edge:RELATED_TO]-(right:GraphNode {nodeType: "TAXPAYER"})
WHERE coalesce(left.riskScore, 0) >= 70
  AND coalesce(right.riskScore, 0) >= 70
RETURN left.id AS sourceTaxpayerId,
       left.label AS sourceTaxpayer,
       right.id AS targetTaxpayerId,
       right.label AS targetTaxpayer,
       edge.edgeType AS relationshipType,
       edge.weight AS relationshipWeight,
       left.riskScore AS sourceRiskScore,
       right.riskScore AS targetRiskScore
ORDER BY relationshipWeight DESC, sourceRiskScore DESC, targetRiskScore DESC
LIMIT 100;
