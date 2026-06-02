export type UserSummary = {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
};

export type LoginResponse = {
  tokenType: string;
  accessToken: string;
  user: UserSummary;
};

export type RiskSignal = {
  id: string;
  taxpayerId: string | null;
  taxpayerPin: string | null;
  taxpayerName: string | null;
  ruleCode: string;
  signalType: string;
  taxHead: string;
  periodStart: string;
  periodEnd: string;
  observedAmount: number;
  declaredAmount: number;
  estimatedGap: number;
  confidenceScore: number;
  severity: string;
  explanation: string;
  evidence: Record<string, unknown>;
  status: string;
  createdAt: string;
};

export type TaxGapRanking = {
  taxpayerId: string;
  taxpayerPin: string;
  taxpayerName: string;
  periodStart: string;
  periodEnd: string;
  score: number;
  confidenceScore: number;
  estimatedGap: number;
  estimatedRecoverableTax: number;
  estimatedTotalDue: number;
  mainFactors: Record<string, unknown>;
};

export type TaxGapSummary = {
  taxHead: string;
  signalCount: number;
  estimatedGap: number;
  estimatedRecoverableTax: number;
  averageConfidence: number;
};

export type CaseRecord = {
  id: string;
  caseNumber: string;
  riskSignalId: string | null;
  taxpayerId: string | null;
  taxpayerPin: string | null;
  taxpayerName: string | null;
  title: string;
  caseType: string;
  priority: string;
  status: string;
  estimatedRecoverableAmount: number;
  assignedTo: string | null;
  assignedOfficerName: string | null;
  openedAt: string;
  closedAt: string | null;
  closureReason: string | null;
  assessedAmount: number | null;
  agreedAmount: number | null;
  collectedAmount: number | null;
};

export type CaseEvent = {
  id: string;
  caseId: string;
  eventType: string;
  note: string;
  createdBy: string | null;
  createdByName: string | null;
  createdAt: string;
};

export type EvidencePack = {
  id: string;
  caseId: string;
  version: number;
  summary?: string;
  status?: string;
  fileUri: string | null;
  content?: Record<string, unknown>;
  evidence?: Record<string, unknown>;
  generatedAt: string;
  generatedBy: string | null;
  generatedByName: string | null;
};

export type CaseDetail = {
  detail: CaseRecord;
  events: CaseEvent[];
  evidencePacks: EvidencePack[];
};

export type IngestionJob = {
  id: string;
  dataSourceId: string;
  dataSourceCode: string;
  dataSourceName: string;
  fileName: string;
  targetTable: string;
  status: string;
  recordsReceived: number;
  recordsValid: number;
  recordsInvalid: number;
  startedAt: string;
  completedAt: string | null;
  errorSummary: string | null;
};

export type DataSource = {
  id: string;
  code: string;
  name: string;
  sourceType: string;
  ownerAgency: string;
  ingestionMethod: string;
  schemaVersion: string;
  active: boolean;
};

export type RuleDefinition = {
  code: string;
  name: string;
  description: string;
  taxHead: string;
  severity: string;
  active: boolean;
  thresholdJson: Record<string, unknown>;
};

export type ReconciliationResult = {
  id: string;
  reconciliationDate: string;
  collectingAgency: string;
  revenueChannel: string;
  expectedAmount: number;
  settledAmount: number;
  varianceAmount: number;
  transactionCount: number;
  settlementCount: number;
  settlementStatus: string;
  expectedSettlementAccount: string | null;
  settlementAccount: string | null;
  maxSettlementLagDays: number | null;
  evidence: Record<string, unknown>;
  riskSignalId: string | null;
  createdAt: string;
};

export type ReconciliationSummary = {
  expectedAmount: number;
  settledAmount: number;
  varianceAmount: number;
  resultCount: number;
  exceptionCount: number;
  missingCount: number;
  delayedCount: number;
  duplicateCount: number;
  wrongAccountCount: number;
};

export type ReconciliationRun = {
  from: string;
  to: string;
  resultsTouched: number;
  exceptions: number;
  riskSignalsTouched: number;
};

export type NotificationTemplate = {
  id: string;
  code: string;
  channel: string;
  subjectTemplate: string | null;
  bodyTemplate: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type NotificationRecord = {
  id: string;
  taxpayerId: string | null;
  caseId: string | null;
  riskSignalId: string | null;
  channel: string;
  templateCode: string;
  recipient: string;
  subject: string | null;
  messageBody: string;
  status: string;
  deliveryProvider: string | null;
  deliveryReference: string | null;
  responseStatus: string | null;
  responseBody: string | null;
  sentAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ModelPrediction = {
  id: string;
  modelVersionId: string;
  taxpayerId: string;
  kraPin: string;
  legalName: string;
  predictionType: string;
  score: number;
  explanation: {
    combinedScore?: number;
    ruleScore?: number;
    peerGroupPercentile?: number;
    mainContributingFeatures?: string[];
    featureContributions?: Record<string, number>;
    officerReviewRequired?: boolean;
    [key: string]: unknown;
  };
  createdAt: string;
};

export type ModelVersion = {
  id: string;
  modelName: string;
  version: string;
  modelType: string;
  trainingDataSummary: string;
  metrics: Record<string, unknown>;
  active: boolean;
  createdAt: string;
};

export type RiskScoringDashboard = {
  activeModelVersion: string;
  predictionCount: number;
  highRiskCount: number;
  averageModelScore: number;
  averageCombinedScore: number;
  topPredictions: ModelPrediction[];
};

export type RiskScoringRun = {
  modelVersionId: string;
  modelName: string;
  modelVersion: string;
  taxpayersScored: number;
  predictionsCreated: number;
  combinedScoresCreated: number;
};

export type GraphNode = {
  id: string;
  nodeType: string;
  label: string;
  riskScore: number;
};

export type GraphEdge = {
  id: string;
  sourceType: string;
  sourceId: string;
  sourceLabel: string;
  targetType: string;
  targetId: string;
  targetLabel: string;
  edgeType: string;
  weight: number;
  source: string;
  evidence: Record<string, unknown>;
  createdAt: string;
};

export type HighRiskCluster = {
  clusterKey: string;
  sourceTaxpayerId: string;
  sourceTaxpayerName: string;
  targetTaxpayerId: string;
  targetTaxpayerName: string;
  edgeType: string;
  edgeWeight: number;
  sourceRiskScore: number;
  targetRiskScore: number;
  reasons: string[];
};

export type TaxpayerGraph = {
  taxpayerId: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  highRiskClusters: HighRiskCluster[];
};

export type GraphExtractionRun = {
  taxpayerRelationshipEdges: number;
  invoiceTradeEdges: number;
  withholdingFlowEdges: number;
  sharedIdentifierEdges: number;
  permitEdges: number;
  paymentChannelEdges: number;
  importActivityEdges: number;
  highRiskClustersDetected: number;
};

export type AdminGovernanceOverview = {
  usersCount: number;
  rolesCount: number;
  permissionsCount: number;
  rolePermissionMappingsCount: number;
  dataSourcesCount: number;
  riskRulesCount: number;
  modelVersionsCount: number;
  auditLogsCount: number;
  activeRetentionPoliciesCount: number;
  privacyChecklistCount: number;
  privacyChecklistCompletedCount: number;
  privacyChecklistCompleted: boolean;
  keycloakMfaStatus: string;
};

export type AdminRole = {
  code: string;
  name: string;
  description: string;
  userCount: number;
  permissionCount: number;
};

export type AdminPermission = {
  code: string;
  description: string;
  roleCount: number;
};

export type RolePermission = {
  roleCode: string;
  roleName: string;
  permissions: string[];
};

export type AuditLogRecord = {
  id: string;
  action: string;
  actorEmail: string | null;
  entityType: string | null;
  entityId: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  details: string;
  createdAt: string;
};

export type DataRetentionPolicy = {
  id: string;
  dataCategory: string;
  retentionDays: number;
  policyReason: string;
  active: boolean;
  createdAt: string;
};

export type PrivacyImpactItem = {
  id: string;
  dataCategory: string;
  purpose: string;
  lawfulBasis: string;
  dataMinimizationNote: string;
  maskingRequired: boolean;
  completed: boolean;
  createdAt: string;
};

export type ExportControl = {
  requiredPermission: string;
  allowedRoles: string[];
  policy: string;
  bulkExportPermissionControlled: boolean;
};

export type KeycloakMfaPath = {
  provider: string;
  status: string;
  configurationPath: string;
  pilotRoles: string[];
  mfaRequiredForPrivilegedRoles: boolean;
};

export type AdminGovernanceDashboard = {
  overview: AdminGovernanceOverview;
  roles: AdminRole[];
  permissions: AdminPermission[];
  rolePermissions: RolePermission[];
  auditLogs: AuditLogRecord[];
  retentionPolicies: DataRetentionPolicy[];
  privacyImpactChecklist: PrivacyImpactItem[];
  exportControls: ExportControl;
  keycloakMfa: KeycloakMfaPath;
};

export type TaxGapBySectorReport = {
  sectorCode: string;
  sectorName: string;
  taxHead: string;
  estimateCount: number;
  taxpayerCount: number;
  estimatedGap: number;
  estimatedRecoverableTax: number;
  estimatedTotalDue: number;
  averageConfidence: number;
};

export type TaxGapByRegionReport = {
  region: string;
  taxHead: string;
  estimateCount: number;
  taxpayerCount: number;
  estimatedGap: number;
  estimatedRecoverableTax: number;
  estimatedTotalDue: number;
  averageConfidence: number;
};

export type OfficerProductivityReport = {
  officerId: string;
  officerName: string;
  assignedCases: number;
  openCases: number;
  closedCases: number;
  assessedAmount: number;
  agreedAmount: number;
  collectedAmount: number;
  averageCaseAgeDays: number;
};

export type RevenueRecoveryReport = {
  period: string;
  taxHead: string;
  recoveryRecords: number;
  assessedAmount: number;
  agreedAmount: number;
  collectedAmount: number;
};

export type AuditPipelineReport = {
  status: string;
  caseCount: number;
  estimatedRecoverableAmount: number;
  assessedAmount: number;
  collectedAmount: number;
};

export type TaxpayerProfile = {
  taxpayerId: string;
  kraPin: string;
  taxpayerType: string;
  legalName: string;
  tradingName: string | null;
  registrationNumber: string | null;
  sectorName: string | null;
  county: string | null;
  identifiers: Array<{
    identifierType: string;
    identifierValue: string;
    source: string;
    confidenceScore: number;
  }>;
  recordCounts: Record<string, number>;
  totals: Record<string, number>;
  relationships: Array<{
    relatedTaxpayerId: string;
    relatedPersonName: string;
    relationshipType: string;
    source: string;
    confidenceScore: number;
  }>;
};
