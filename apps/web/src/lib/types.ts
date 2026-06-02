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
