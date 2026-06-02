"use client";

import {
  ColumnDef,
  SortingState,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  ArrowDownUp,
  BadgeCheck,
  BrainCircuit,
  BriefcaseBusiness,
  Database,
  FileJson,
  FileText,
  Gavel,
  Loader2,
  Lock,
  LogIn,
  LogOut,
  Mail,
  MessageSquareReply,
  Network,
  Search,
  Send,
  Settings2,
  ShieldCheck,
  UserRoundSearch,
} from "lucide-react";
import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
  ApiError,
  addCaseNote,
  createCase,
  downloadEvidencePackPdf,
  downloadTaxGapBySectorExport,
  generateEvidencePack,
  getAdminGovernance,
  getAuditPipeline,
  getCaseDetail,
  getCases,
  getDataSources,
  getIngestionJobs,
  getNotificationTemplates,
  getNotifications,
  getOfficerProductivity,
  getRevenueRecovery,
  getModelPredictions,
  getModelVersions,
  getRiskScoringDashboard,
  getReconciliationResults,
  getReconciliationSummary,
  getRiskSignals,
  getRules,
  getTaxGapByRegion,
  getTaxGapBySector,
  getTaxGapRanking,
  getTaxGapSummary,
  getTaxpayerGraph,
  getTaxpayerProfile,
  getMe,
  login,
  openReconciliationCase,
  recordNotificationResponse,
  runGraphExtraction,
  runReconciliation,
  runRiskScoring,
  sendCaseNudge,
  sendRiskNudge,
} from "@/lib/api";
import {
  demoCaseDetail,
  demoAdminGovernance,
  demoAuditPipeline,
  demoCases,
  demoDataSources,
  demoGraph,
  demoIngestionJobs,
  demoNotificationTemplates,
  demoNotifications,
  demoOfficerProductivity,
  demoRevenueRecovery,
  demoModelPredictions,
  demoModelVersions,
  demoProfile,
  demoRiskScoringDashboard,
  demoRanking,
  demoReconciliationResults,
  demoReconciliationSummary,
  demoRules,
  demoSignals,
  demoSummary,
  demoTaxGapByRegion,
  demoTaxGapBySector,
  demoUser,
} from "@/lib/demo-data";
import type {
  AdminGovernanceDashboard,
  AuditPipelineReport,
  CaseDetail,
  CaseRecord,
  DataSource,
  GraphEdge,
  IngestionJob,
  ModelPrediction,
  ModelVersion,
  NotificationRecord,
  NotificationTemplate,
  OfficerProductivityReport,
  ReconciliationResult,
  ReconciliationSummary,
  RevenueRecoveryReport,
  RiskScoringDashboard,
  RiskSignal,
  RuleDefinition,
  TaxGapByRegionReport,
  TaxGapBySectorReport,
  TaxGapRanking,
  TaxGapSummary,
  TaxpayerGraph,
  TaxpayerProfile,
  UserSummary,
} from "@/lib/types";

type ViewKey =
  | "overview"
  | "risks"
  | "taxpayers"
  | "cases"
  | "evidence"
  | "settlements"
  | "notifications"
  | "ai-scoring"
  | "reports"
  | "ingestion"
  | "rules"
  | "governance";

const navItems: Array<{ key: ViewKey; label: string; icon: typeof Activity }> = [
  { icon: Activity, key: "overview", label: "Overview" },
  { icon: AlertTriangle, key: "risks", label: "Risk Queue" },
  { icon: UserRoundSearch, key: "taxpayers", label: "Taxpayers" },
  { icon: BriefcaseBusiness, key: "cases", label: "Cases" },
  { icon: FileJson, key: "evidence", label: "Evidence" },
  { icon: BadgeCheck, key: "settlements", label: "Settlements" },
  { icon: Mail, key: "notifications", label: "Nudges" },
  { icon: BrainCircuit, key: "ai-scoring", label: "AI Scoring" },
  { icon: FileText, key: "reports", label: "Reports" },
  { icon: Database, key: "ingestion", label: "Ingestion" },
  { icon: Settings2, key: "rules", label: "Rules" },
  { icon: ShieldCheck, key: "governance", label: "Governance" },
];

function money(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "KES 0";
  }

  return new Intl.NumberFormat("en-KE", {
    currency: "KES",
    maximumFractionDigits: 0,
    style: "currency",
  }).format(Number(value));
}

function percent(value: number | null | undefined) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "0%";
  }
  return `${Math.round(Number(value))}%`;
}

function shortDate(value: string | null | undefined) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("en-KE", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(value));
}

function statusTone(value: string) {
  const normalized = value.toUpperCase();
  if (normalized.includes("HIGH") || normalized.includes("OPEN")) {
    return "border-exposure/30 bg-exposure/10 text-exposure";
  }
  if (
    normalized.includes("RUNNING") ||
    normalized.includes("REVIEW") ||
    normalized.includes("AWAITING")
  ) {
    return "border-revenue/30 bg-revenue/10 text-revenue";
  }
  return "border-assurance/30 bg-assurance/10 text-assurance";
}

function useAuthedQuery<T>(
  key: readonly unknown[],
  token: string | null,
  queryFn: (token: string) => Promise<T>,
  fallback: T,
) {
  const query = useQuery({
    enabled: Boolean(token),
    queryFn: () => queryFn(token as string),
    queryKey: key,
  });

  return {
    ...query,
    data: query.data ?? fallback,
    isDemo: !token || query.isError,
    liveData: query.data,
  };
}

export function DashboardApp() {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<UserSummary | null>(null);
  const [activeView, setActiveView] = useState<ViewKey>("overview");
  const [riskFilter, setRiskFilter] = useState("");
  const [caseFilter, setCaseFilter] = useState("");
  const [taxpayerSearch, setTaxpayerSearch] = useState("");
  const [selectedTaxpayerId, setSelectedTaxpayerId] = useState<string>(
    demoSignals[0].taxpayerId ?? "",
  );
  const [selectedCaseId, setSelectedCaseId] = useState<string>(demoCases[0].id);
  const [selectedSignalId, setSelectedSignalId] = useState<string>(demoSignals[0].id);
  const [note, setNote] = useState("");
  const [responseStatus, setResponseStatus] = useState("DOCUMENTS_SUBMITTED");
  const [responseBody, setResponseBody] = useState("");
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    const storedToken = window.localStorage.getItem("kra-token");
    if (!storedToken) {
      return;
    }

    let cancelled = false;
    getMe(storedToken)
      .then((storedUser) => {
        if (cancelled) {
          return;
        }
        setToken(storedToken);
        setUser(storedUser);
        window.localStorage.setItem("kra-user", JSON.stringify(storedUser));
      })
      .catch(() => {
        window.localStorage.removeItem("kra-token");
        window.localStorage.removeItem("kra-user");
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const signalsQuery = useAuthedQuery(["signals", token], token, getRiskSignals, demoSignals);
  const rankingQuery = useAuthedQuery(["ranking", token], token, getTaxGapRanking, demoRanking);
  const summaryQuery = useAuthedQuery(["summary", token], token, getTaxGapSummary, demoSummary);
  const casesQuery = useAuthedQuery(["cases", token], token, getCases, demoCases);
  const ingestionQuery = useAuthedQuery(
    ["ingestion", token],
    token,
    getIngestionJobs,
    demoIngestionJobs,
  );
  const dataSourcesQuery = useAuthedQuery(
    ["data-sources", token],
    token,
    getDataSources,
    demoDataSources,
  );
  const rulesQuery = useAuthedQuery(["rules", token], token, getRules, demoRules);
  const reconciliationSummaryQuery = useAuthedQuery(
    ["reconciliation-summary", token],
    token,
    getReconciliationSummary,
    demoReconciliationSummary,
  );
  const reconciliationResultsQuery = useAuthedQuery(
    ["reconciliation-results", token],
    token,
    getReconciliationResults,
    demoReconciliationResults,
  );
  const notificationTemplatesQuery = useAuthedQuery(
    ["notification-templates", token],
    token,
    getNotificationTemplates,
    demoNotificationTemplates,
  );
  const notificationsQuery = useAuthedQuery(
    ["notifications", token],
    token,
    getNotifications,
    demoNotifications,
  );
  const riskScoringDashboardQuery = useAuthedQuery(
    ["risk-scoring-dashboard", token],
    token,
    getRiskScoringDashboard,
    demoRiskScoringDashboard,
  );
  const modelPredictionsQuery = useAuthedQuery(
    ["model-predictions", token],
    token,
    getModelPredictions,
    demoModelPredictions,
  );
  const modelVersionsQuery = useAuthedQuery(
    ["model-versions", token],
    token,
    getModelVersions,
    demoModelVersions,
  );
  const adminGovernanceQuery = useAuthedQuery(
    ["admin-governance", token],
    token,
    getAdminGovernance,
    demoAdminGovernance,
  );
  const taxGapBySectorQuery = useAuthedQuery(
    ["reports-tax-gap-sector", token],
    token,
    getTaxGapBySector,
    demoTaxGapBySector,
  );
  const taxGapByRegionQuery = useAuthedQuery(
    ["reports-tax-gap-region", token],
    token,
    getTaxGapByRegion,
    demoTaxGapByRegion,
  );
  const officerProductivityQuery = useAuthedQuery(
    ["reports-officer-productivity", token],
    token,
    getOfficerProductivity,
    demoOfficerProductivity,
  );
  const revenueRecoveryQuery = useAuthedQuery(
    ["reports-revenue-recovery", token],
    token,
    getRevenueRecovery,
    demoRevenueRecovery,
  );
  const auditPipelineQuery = useAuthedQuery(
    ["reports-audit-pipeline", token],
    token,
    getAuditPipeline,
    demoAuditPipeline,
  );
  const liveCaseSelected = Boolean(
    selectedCaseId && casesQuery.liveData?.some((record) => record.id === selectedCaseId),
  );
  const liveTaxpayerIds = useMemo(
    () =>
      [
        ...(rankingQuery.liveData?.map((record) => record.taxpayerId) ?? []),
        ...(signalsQuery.liveData?.map((signal) => signal.taxpayerId) ?? []),
      ].filter((taxpayerId): taxpayerId is string => Boolean(taxpayerId)),
    [rankingQuery.liveData, signalsQuery.liveData],
  );
  const liveTaxpayerSelected = liveTaxpayerIds.includes(selectedTaxpayerId);

  const profileQuery = useQuery({
    enabled: Boolean(token && liveTaxpayerSelected),
    queryFn: () => getTaxpayerProfile(token as string, selectedTaxpayerId),
    queryKey: ["taxpayer-profile", token, selectedTaxpayerId],
  });
  const taxpayerGraphQuery = useQuery({
    enabled: Boolean(token && liveTaxpayerSelected),
    queryFn: () => getTaxpayerGraph(token as string, selectedTaxpayerId),
    queryKey: ["taxpayer-graph", token, selectedTaxpayerId],
  });

  const caseDetailQuery = useQuery({
    enabled: Boolean(token && liveCaseSelected),
    queryFn: () => getCaseDetail(token as string, selectedCaseId),
    queryKey: ["case-detail", token, selectedCaseId],
  });

  const activeUser = user ?? demoUser;
  const isAdmin = activeUser.roles.includes("ADMIN") || activeUser.roles.includes("ROLE_ADMIN");
  const riskSignals = signalsQuery.data;
  const cases = casesQuery.data;
  const caseDetail = caseDetailQuery.data ?? detailFallback(cases, selectedCaseId);
  const profile = profileQuery.data ?? profileFallback(rankingQuery.data, selectedTaxpayerId);
  const taxpayerGraph = taxpayerGraphQuery.data ?? graphFallback(profile, selectedTaxpayerId);
  const selectedSignal =
    riskSignals.find((signal) => signal.id === selectedSignalId) ?? riskSignals[0];
  const selectedCase = cases.find((record) => record.id === selectedCaseId) ?? cases[0];
  const liveMode = Boolean(token) && !signalsQuery.isError && !casesQuery.isError;

  useEffect(() => {
    if (!token || casesQuery.isError || !casesQuery.liveData?.length) {
      return;
    }

    if (!casesQuery.liveData.some((record) => record.id === selectedCaseId)) {
      setSelectedCaseId(casesQuery.liveData[0].id);
    }
  }, [casesQuery.isError, casesQuery.liveData, selectedCaseId, token]);

  useEffect(() => {
    if (!token || signalsQuery.isError || !signalsQuery.liveData?.length) {
      return;
    }

    if (!signalsQuery.liveData.some((signal) => signal.id === selectedSignalId)) {
      setSelectedSignalId(signalsQuery.liveData[0].id);
    }
  }, [selectedSignalId, signalsQuery.isError, signalsQuery.liveData, token]);

  useEffect(() => {
    if (!token) {
      return;
    }

    if (liveTaxpayerIds.length && !liveTaxpayerIds.includes(selectedTaxpayerId)) {
      setSelectedTaxpayerId(liveTaxpayerIds[0]);
    }
  }, [liveTaxpayerIds, selectedTaxpayerId, token]);

  useEffect(() => {
    const authError = [
      signalsQuery.error,
      rankingQuery.error,
      summaryQuery.error,
      casesQuery.error,
      ingestionQuery.error,
      dataSourcesQuery.error,
      rulesQuery.error,
      reconciliationSummaryQuery.error,
      reconciliationResultsQuery.error,
      notificationTemplatesQuery.error,
      notificationsQuery.error,
      riskScoringDashboardQuery.error,
      modelPredictionsQuery.error,
      modelVersionsQuery.error,
    ].some((error) => error instanceof ApiError && [401, 403].includes(error.status));

    if (!authError) {
      return;
    }

    window.localStorage.removeItem("kra-token");
    window.localStorage.removeItem("kra-user");
    setToken(null);
    setUser(null);
    queryClient.clear();
    setToast("Session expired. Sign in again.");
  }, [
    casesQuery.error,
    dataSourcesQuery.error,
    ingestionQuery.error,
    notificationTemplatesQuery.error,
    notificationsQuery.error,
    riskScoringDashboardQuery.error,
    modelPredictionsQuery.error,
    modelVersionsQuery.error,
    queryClient,
    rankingQuery.error,
    reconciliationResultsQuery.error,
    reconciliationSummaryQuery.error,
    rulesQuery.error,
    signalsQuery.error,
    summaryQuery.error,
  ]);

  const createCaseMutation = useMutation({
    mutationFn: (riskSignalId: string) => createCase(token as string, riskSignalId, "HIGH"),
    onSuccess: (record) => {
      setSelectedCaseId(record.id);
      setActiveView("cases");
      queryClient.invalidateQueries({ queryKey: ["cases", token] });
      setToast(`Case ${record.caseNumber} opened`);
    },
  });

  const noteMutation = useMutation({
    mutationFn: () => addCaseNote(token as string, selectedCaseId, note),
    onSuccess: () => {
      setNote("");
      queryClient.invalidateQueries({ queryKey: ["case-detail", token, selectedCaseId] });
      setToast("Case note added");
    },
  });

  const evidenceMutation = useMutation({
    mutationFn: () => generateEvidencePack(token as string, selectedCaseId),
    onSuccess: (pack) => {
      queryClient.setQueryData<CaseDetail>(["case-detail", token, selectedCaseId], (current) => {
        if (!current) {
          return current;
        }

        return {
          ...current,
          evidencePacks: [
            pack,
            ...current.evidencePacks.filter((existing) => existing.id !== pack.id),
          ],
        };
      });
      queryClient.invalidateQueries({ queryKey: ["case-detail", token, selectedCaseId] });
      setActiveView("evidence");
      setToast("Evidence pack generated");
    },
  });

  const reconciliationMutation = useMutation({
    mutationFn: () => runReconciliation(token as string),
    onSuccess: (run) => {
      queryClient.invalidateQueries({ queryKey: ["reconciliation-summary", token] });
      queryClient.invalidateQueries({ queryKey: ["reconciliation-results", token] });
      queryClient.invalidateQueries({ queryKey: ["signals", token] });
      setToast(`Reconciliation complete: ${run.exceptions} exceptions`);
    },
  });

  const reconciliationCaseMutation = useMutation({
    mutationFn: (resultId: string) => openReconciliationCase(token as string, resultId),
    onSuccess: (record) => {
      setSelectedCaseId(record.id);
      setCaseFilter(record.caseNumber);
      setActiveView("cases");
      queryClient.setQueryData<CaseRecord[]>(["cases", token], (current) => {
        if (!current) {
          return [record];
        }
        return [record, ...current.filter((existing) => existing.id !== record.id)];
      });
      queryClient.invalidateQueries({ queryKey: ["cases", token] });
      queryClient.invalidateQueries({ queryKey: ["reconciliation-results", token] });
      setToast(`Settlement case ${record.caseNumber} opened`);
    },
  });

  const caseNudgeMutation = useMutation({
    mutationFn: (channel: string) => sendCaseNudge(token as string, selectedCaseId, channel),
    onSuccess: (record) => {
      queryClient.setQueryData<NotificationRecord[]>(["notifications", token], (current) => [
        record,
        ...(current ?? []).filter((existing) => existing.id !== record.id),
      ]);
      queryClient.invalidateQueries({ queryKey: ["notifications", token] });
      setToast(`${record.channel} nudge sent for ${selectedCase?.caseNumber ?? "case"}`);
    },
  });

  const riskNudgeMutation = useMutation({
    mutationFn: (channel: string) => sendRiskNudge(token as string, selectedSignalId, channel),
    onSuccess: (record) => {
      queryClient.setQueryData<NotificationRecord[]>(["notifications", token], (current) => [
        record,
        ...(current ?? []).filter((existing) => existing.id !== record.id),
      ]);
      queryClient.invalidateQueries({ queryKey: ["notifications", token] });
      setToast(`${record.channel} nudge sent for ${selectedSignal?.ruleCode ?? "risk signal"}`);
    },
  });

  const responseMutation = useMutation({
    mutationFn: (notificationId: string) =>
      recordNotificationResponse(token as string, notificationId, responseStatus, responseBody),
    onSuccess: (record) => {
      setResponseBody("");
      queryClient.setQueryData<NotificationRecord[]>(["notifications", token], (current) =>
        (current ?? []).map((existing) => (existing.id === record.id ? record : existing)),
      );
      queryClient.invalidateQueries({ queryKey: ["notifications", token] });
      setToast("Taxpayer response recorded");
    },
  });

  const riskScoringMutation = useMutation({
    mutationFn: () => runRiskScoring(token as string),
    onError: (error) => {
      if (error instanceof ApiError && [401, 403].includes(error.status)) {
        window.localStorage.removeItem("kra-token");
        window.localStorage.removeItem("kra-user");
        setToken(null);
        setUser(null);
        queryClient.clear();
        setToast("Sign in again to run live AI scoring.");
        return;
      }

      setToast(scoringErrorMessage(error));
    },
    onSuccess: (run) => {
      queryClient.invalidateQueries({ queryKey: ["risk-scoring-dashboard", token] });
      queryClient.invalidateQueries({ queryKey: ["model-predictions", token] });
      queryClient.invalidateQueries({ queryKey: ["model-versions", token] });
      setToast(`AI scoring complete: ${run.predictionsCreated} predictions`);
    },
  });

  const reportExportMutation = useMutation({
    mutationFn: async (format: "csv" | "xlsx" | "pdf") => {
      if (!token) {
        throw new Error("Sign in to export live reports.");
      }
      const blob = await downloadTaxGapBySectorExport(token, format);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `tax-gap-by-sector.${format}`;
      link.click();
      window.URL.revokeObjectURL(url);
      return format;
    },
    onError: (error) => {
      setToast(error instanceof Error ? error.message : "Report export failed.");
    },
    onSuccess: (format) => setToast(`Tax gap by sector ${format.toUpperCase()} exported.`),
  });
  const graphExtractionMutation = useMutation({
    mutationFn: () => runGraphExtraction(token as string),
    onError: (error) => {
      setToast(
        error instanceof Error
          ? `Graph extraction failed: ${error.message}`
          : "Graph extraction failed.",
      );
    },
    onSuccess: (run) => {
      queryClient.invalidateQueries({ queryKey: ["taxpayer-graph", token] });
      const touched =
        run.invoiceTradeEdges +
        run.withholdingFlowEdges +
        run.sharedIdentifierEdges +
        run.permitEdges +
        run.paymentChannelEdges +
        run.importActivityEdges;
      setToast(`Graph refreshed: ${touched} relationship edges touched`);
    },
  });

  function handleLogout() {
    window.localStorage.removeItem("kra-token");
    window.localStorage.removeItem("kra-user");
    setToken(null);
    setUser(null);
    setToast("Signed out");
  }

  function handleDemoCase(signalId: string) {
    setSelectedSignalId(signalId);
    setSelectedCaseId(demoCases[0].id);
    setActiveView("cases");
    setToast("Demo case preview selected");
  }

  const metrics = useMemo(() => {
    const estimatedGap = riskSignals.reduce(
      (total, signal) => total + Number(signal.estimatedGap ?? 0),
      0,
    );
    const recoverable = rankingQuery.data.reduce(
      (total, record) => total + Number(record.estimatedRecoverableTax ?? 0),
      0,
    );
    const openCases = cases.filter((record) => record.status !== "CLOSED").length;
    const invalidRows = ingestionQuery.data.reduce(
      (total, job) => total + Number(job.recordsInvalid ?? 0),
      0,
    );

    return [
      { label: "Estimated exposure", tone: "text-exposure", value: money(estimatedGap) },
      { label: "Recoverable estimate", tone: "text-authority", value: money(recoverable) },
      { label: "Open cases", tone: "text-revenue", value: String(openCases) },
      { label: "Invalid ingest rows", tone: "text-ink", value: String(invalidRows) },
    ];
  }, [cases, ingestionQuery.data, rankingQuery.data, riskSignals]);

  return (
    <main className="min-h-dvh bg-paper text-ink">
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
          <div>
            <p className="text-sm font-semibold uppercase text-authority">Revenue Intelligence</p>
            <h1 className="mt-1 text-2xl font-semibold leading-tight sm:text-3xl">
              Officer and Executive Dashboard
            </h1>
          </div>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <span
              className={`inline-flex min-h-10 items-center rounded-md border px-3 text-sm font-semibold ${liveMode ? "border-assurance/30 bg-assurance/10 text-assurance" : "border-revenue/30 bg-revenue/10 text-revenue"}`}
            >
              {liveMode ? "Live API" : "Demo data"}
            </span>
            <LoginPanel
              activeUser={activeUser}
              onLogin={(nextToken, nextUser) => {
                setToken(nextToken);
                setUser(nextUser);
                window.localStorage.setItem("kra-token", nextToken);
                window.localStorage.setItem("kra-user", JSON.stringify(nextUser));
                setToast(`Signed in as ${nextUser.fullName}`);
              }}
              onLogout={handleLogout}
              token={token}
            />
          </div>
        </div>
      </header>

      <div className="mx-auto grid w-full max-w-7xl gap-5 px-4 py-5 sm:px-6 lg:grid-cols-[240px_1fr] lg:px-8">
        <aside className="lg:sticky lg:top-4 lg:self-start">
          <nav
            aria-label="Dashboard sections"
            className="grid gap-2 rounded-md border border-line bg-white p-2 shadow-panel sm:grid-cols-2 lg:grid-cols-1"
          >
            {navItems.map((item) => {
              const Icon = item.icon;
              const selected = activeView === item.key;

              return (
                <button
                  aria-current={selected ? "page" : undefined}
                  className={`flex min-h-11 items-center gap-3 rounded-md px-3 py-2 text-left text-sm font-semibold transition ${selected ? "bg-authority text-white" : "text-ink hover:bg-paper"}`}
                  data-testid={`nav-${item.key}`}
                  key={item.key}
                  onClick={() => setActiveView(item.key)}
                  type="button"
                >
                  <Icon size={18} aria-hidden="true" />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </aside>

        <section className="min-w-0 space-y-5" aria-live="polite">
          {toast ? (
            <div className="flex items-center gap-2 rounded-md border border-assurance/30 bg-assurance/10 px-4 py-3 text-sm font-semibold text-assurance">
              <BadgeCheck size={18} aria-hidden="true" />
              {toast}
            </div>
          ) : null}

          {activeView === "overview" ? (
            <OverviewView
              ingestionJobs={ingestionQuery.data}
              metrics={metrics}
              riskSignals={riskSignals}
              summary={summaryQuery.data}
            />
          ) : null}

          {activeView === "risks" ? (
            <RiskQueueView
              filter={riskFilter}
              isMutating={createCaseMutation.isPending}
              onCreateCase={(signalId) => {
                if (token) {
                  createCaseMutation.mutate(signalId);
                } else {
                  handleDemoCase(signalId);
                }
              }}
              onFilter={setRiskFilter}
              onSelectTaxpayer={(taxpayerId) => {
                setSelectedTaxpayerId(taxpayerId);
                setActiveView("taxpayers");
              }}
              rows={riskSignals}
              selectedSignalId={selectedSignal?.id}
              setSelectedSignalId={setSelectedSignalId}
            />
          ) : null}

          {activeView === "taxpayers" ? (
            <TaxpayerView
              graph={taxpayerGraph}
              isGraphPending={graphExtractionMutation.isPending}
              isLiveSession={Boolean(token)}
              onSearch={setTaxpayerSearch}
              onSelectTaxpayer={setSelectedTaxpayerId}
              onRefreshGraph={() => {
                if (token) {
                  graphExtractionMutation.mutate();
                } else {
                  setToast("Sign in to refresh live graph intelligence.");
                }
              }}
              profile={profile}
              ranking={rankingQuery.data}
              search={taxpayerSearch}
              selectedTaxpayerId={selectedTaxpayerId}
            />
          ) : null}

          {activeView === "cases" ? (
            <CasesView
              caseDetail={caseDetail}
              filter={caseFilter}
              isEvidencePending={evidenceMutation.isPending}
              isNotePending={noteMutation.isPending}
              note={note}
              onAddNote={() => {
                if (token && note.trim()) {
                  noteMutation.mutate();
                } else if (note.trim()) {
                  setNote("");
                  setToast("Demo note previewed");
                }
              }}
              onFilter={setCaseFilter}
              onGenerateEvidence={() => {
                if (token) {
                  evidenceMutation.mutate();
                } else {
                  setActiveView("evidence");
                  setToast("Demo evidence preview selected");
                }
              }}
              onNote={setNote}
              onSelectCase={setSelectedCaseId}
              rows={cases}
              selectedCaseId={selectedCase?.id}
            />
          ) : null}

          {activeView === "evidence" ? (
            <EvidenceView caseDetail={caseDetail} token={token} />
          ) : null}

          {activeView === "settlements" ? (
            <SettlementView
              isCasePending={reconciliationCaseMutation.isPending}
              isRunPending={reconciliationMutation.isPending}
              onOpenCase={(resultId) => {
                if (token) {
                  reconciliationCaseMutation.mutate(resultId);
                } else {
                  setToast("Sign in to open settlement cases");
                }
              }}
              onRun={() => {
                if (token) {
                  reconciliationMutation.mutate();
                } else {
                  setToast("Sign in to run reconciliation");
                }
              }}
              results={reconciliationResultsQuery.data}
              summary={reconciliationSummaryQuery.data}
            />
          ) : null}

          {activeView === "notifications" ? (
            <NotificationView
              cases={cases}
              isCasePending={caseNudgeMutation.isPending}
              isResponsePending={responseMutation.isPending}
              isRiskPending={riskNudgeMutation.isPending}
              notifications={notificationsQuery.data}
              onRecordResponse={(notificationId) => {
                if (token && responseBody.trim()) {
                  responseMutation.mutate(notificationId);
                } else if (responseBody.trim()) {
                  setResponseBody("");
                  setToast("Demo taxpayer response previewed");
                }
              }}
              onResponseBody={setResponseBody}
              onResponseStatus={setResponseStatus}
              onSelectCase={setSelectedCaseId}
              onSelectSignal={setSelectedSignalId}
              onSendCaseNudge={(channel) => {
                if (token) {
                  caseNudgeMutation.mutate(channel);
                } else {
                  setToast(`Demo ${channel} case nudge previewed`);
                }
              }}
              onSendRiskNudge={(channel) => {
                if (token) {
                  riskNudgeMutation.mutate(channel);
                } else {
                  setToast(`Demo ${channel} risk nudge previewed`);
                }
              }}
              responseBody={responseBody}
              responseStatus={responseStatus}
              riskSignals={riskSignals}
              selectedCaseId={selectedCase?.id}
              selectedSignalId={selectedSignal?.id}
              templates={notificationTemplatesQuery.data}
            />
          ) : null}

          {activeView === "ai-scoring" ? (
            <AiScoringView
              dashboard={riskScoringDashboardQuery.data}
              isLiveSession={Boolean(token)}
              isRunPending={riskScoringMutation.isPending}
              modelVersions={modelVersionsQuery.data}
              onRun={() => {
                if (token) {
                  riskScoringMutation.mutate();
                } else {
                  setToast("Sign in to run live AI scoring.");
                }
              }}
              onSelectTaxpayer={(taxpayerId) => {
                setSelectedTaxpayerId(taxpayerId);
                setActiveView("taxpayers");
              }}
              predictions={modelPredictionsQuery.data}
              runError={riskScoringMutation.error}
            />
          ) : null}

          {activeView === "reports" ? (
            <ReportsView
              auditPipeline={auditPipelineQuery.data}
              isExporting={reportExportMutation.isPending}
              isLiveSession={Boolean(token)}
              officerProductivity={officerProductivityQuery.data}
              onExport={(format) => {
                if (token) {
                  reportExportMutation.mutate(format);
                } else {
                  setToast("Sign in to export live reports.");
                }
              }}
              revenueRecovery={revenueRecoveryQuery.data}
              taxGapByRegion={taxGapByRegionQuery.data}
              taxGapBySector={taxGapBySectorQuery.data}
            />
          ) : null}

          {activeView === "ingestion" ? (
            <IngestionView dataSources={dataSourcesQuery.data} jobs={ingestionQuery.data} />
          ) : null}

          {activeView === "rules" ? <RulesView isAdmin={isAdmin} rules={rulesQuery.data} /> : null}

          {activeView === "governance" ? (
            <GovernanceView dashboard={adminGovernanceQuery.data} isAdmin={isAdmin} />
          ) : null}
        </section>
      </div>
    </main>
  );
}

function LoginPanel({
  activeUser,
  onLogin,
  onLogout,
  token,
}: {
  activeUser: UserSummary;
  onLogin: (token: string, user: UserSummary) => void;
  onLogout: () => void;
  token: string | null;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => login(email, password),
    onError: () => setError("Login failed. Check the API is running and credentials are valid."),
    onSuccess: (response) => {
      setError(null);
      onLogin(response.accessToken, response.user);
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate();
  }

  if (token) {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-md border border-line bg-paper px-3 py-2 text-sm font-medium">
          {activeUser.fullName}
        </span>
        <button
          className="inline-flex min-h-10 items-center gap-2 rounded-md border border-line bg-white px-3 text-sm font-semibold hover:border-exposure hover:text-exposure"
          onClick={onLogout}
          type="button"
        >
          <LogOut size={17} aria-hidden="true" />
          Sign out
        </button>
      </div>
    );
  }

  return (
    <form className="flex flex-col gap-2 sm:flex-row sm:items-start" onSubmit={submit}>
      <label className="sr-only" htmlFor="email">
        Email
      </label>
      <input
        autoComplete="email"
        className="min-h-10 rounded-md border border-line bg-white px-3 text-sm focus:border-authority"
        id="email"
        onChange={(event) => setEmail(event.target.value)}
        placeholder="admin email"
        type="email"
        value={email}
      />
      <label className="sr-only" htmlFor="password">
        Password
      </label>
      <input
        autoComplete="current-password"
        className="min-h-10 rounded-md border border-line bg-white px-3 text-sm focus:border-authority"
        id="password"
        onChange={(event) => setPassword(event.target.value)}
        placeholder="password"
        type="password"
        value={password}
      />
      <button
        className="inline-flex min-h-10 items-center justify-center gap-2 rounded-md bg-authority px-3 text-sm font-semibold text-white hover:bg-[#1d4a40] disabled:cursor-not-allowed disabled:opacity-50"
        disabled={mutation.isPending}
        type="submit"
      >
        {mutation.isPending ? (
          <Loader2 className="animate-spin" size={17} aria-hidden="true" />
        ) : (
          <LogIn size={17} aria-hidden="true" />
        )}
        Sign in
      </button>
      {error ? <p className="max-w-64 text-sm font-medium text-exposure">{error}</p> : null}
    </form>
  );
}

function OverviewView({
  ingestionJobs,
  metrics,
  riskSignals,
  summary,
}: {
  ingestionJobs: IngestionJob[];
  metrics: Array<{ label: string; tone: string; value: string }>;
  riskSignals: RiskSignal[];
  summary: TaxGapSummary[];
}) {
  const [chartReady, setChartReady] = useState(false);
  const chartData = summary.map((item) => ({
    gap: Number(item.estimatedGap ?? 0),
    recoverable: Number(item.estimatedRecoverableTax ?? 0),
    taxHead: item.taxHead.replace("_", " "),
  }));

  useEffect(() => {
    setChartReady(true);
  }, []);

  return (
    <div className="space-y-5">
      <Section title="Operational Snapshot">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {metrics.map((metric) => (
            <article
              className="rounded-md border border-line bg-white p-4 shadow-panel"
              key={metric.label}
            >
              <p className="text-sm font-medium text-gray-700">{metric.label}</p>
              <p className={`mt-3 text-2xl font-semibold ${metric.tone}`}>{metric.value}</p>
            </article>
          ))}
        </div>
      </Section>

      <div className="grid gap-5 xl:grid-cols-[1.15fr_0.85fr]">
        <Section title="Gap by Tax Head">
          <div className="h-80 min-w-0 overflow-hidden">
            {chartReady && chartData.length ? (
              <ResponsiveContainer height="100%" minHeight={320} minWidth={0} width="100%">
                <BarChart data={chartData} margin={{ bottom: 8, left: 8, right: 12, top: 12 }}>
                  <CartesianGrid stroke="#d9ded7" vertical={false} />
                  <XAxis dataKey="taxHead" tickLine={false} />
                  <YAxis tickFormatter={(value) => `${Number(value) / 1_000_000}M`} width={48} />
                  <Tooltip formatter={(value) => money(Number(value))} />
                  <Bar dataKey="gap" fill="#a33b2f" name="Estimated gap" radius={[4, 4, 0, 0]} />
                  <Bar
                    dataKey="recoverable"
                    fill="#245c4f"
                    name="Recoverable"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-full items-center justify-center rounded-md border border-line bg-paper px-4 text-center text-sm font-medium text-gray-700">
                No tax gap summary is available yet.
              </div>
            )}
          </div>
        </Section>

        <Section title="Current Flow">
          <div className="grid gap-3">
            <FlowItem
              icon={<AlertTriangle size={20} />}
              label="Open risk signals"
              value={String(riskSignals.length)}
            />
            <FlowItem
              icon={<Database size={20} />}
              label="Ingestion jobs"
              value={String(ingestionJobs.length)}
            />
            <FlowItem
              icon={<ShieldCheck size={20} />}
              label="Completed jobs"
              value={String(ingestionJobs.filter((job) => job.status === "COMPLETED").length)}
            />
          </div>
        </Section>
      </div>
    </div>
  );
}

function ReportsView({
  auditPipeline,
  isExporting,
  isLiveSession,
  officerProductivity,
  onExport,
  revenueRecovery,
  taxGapByRegion,
  taxGapBySector,
}: {
  auditPipeline: AuditPipelineReport[];
  isExporting: boolean;
  isLiveSession: boolean;
  officerProductivity: OfficerProductivityReport[];
  onExport: (format: "csv" | "xlsx" | "pdf") => void;
  revenueRecovery: RevenueRecoveryReport[];
  taxGapByRegion: TaxGapByRegionReport[];
  taxGapBySector: TaxGapBySectorReport[];
}) {
  const [chartReady, setChartReady] = useState(false);
  const [taxHeadFilter, setTaxHeadFilter] = useState("ALL");
  const [sectorFilter, setSectorFilter] = useState("");
  const [regionFilter, setRegionFilter] = useState("");
  const [periodFilter, setPeriodFilter] = useState("ALL");
  const [officerFilter, setOfficerFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");

  useEffect(() => {
    setChartReady(true);
  }, []);

  const taxHeads = uniqueOptions([
    ...taxGapBySector.map((row) => row.taxHead),
    ...taxGapByRegion.map((row) => row.taxHead),
    ...revenueRecovery.map((row) => row.taxHead),
  ]);
  const periods = uniqueOptions(revenueRecovery.map((row) => row.period));
  const officers = uniqueOptions(officerProductivity.map((row) => row.officerName));
  const statuses = uniqueOptions(auditPipeline.map((row) => row.status));

  const filteredSector = taxGapBySector.filter(
    (row) =>
      (taxHeadFilter === "ALL" || row.taxHead === taxHeadFilter) &&
      row.sectorName.toLowerCase().includes(sectorFilter.toLowerCase()),
  );
  const filteredRegion = taxGapByRegion.filter(
    (row) =>
      (taxHeadFilter === "ALL" || row.taxHead === taxHeadFilter) &&
      row.region.toLowerCase().includes(regionFilter.toLowerCase()),
  );
  const filteredRecovery = revenueRecovery.filter(
    (row) =>
      (taxHeadFilter === "ALL" || row.taxHead === taxHeadFilter) &&
      (periodFilter === "ALL" || row.period === periodFilter),
  );
  const filteredOfficers = officerProductivity.filter(
    (row) => officerFilter === "ALL" || row.officerName === officerFilter,
  );
  const filteredPipeline = auditPipeline.filter(
    (row) => statusFilter === "ALL" || row.status === statusFilter,
  );

  const totalGap = filteredSector.reduce((sum, row) => sum + Number(row.estimatedGap), 0);
  const totalRecoverable = filteredSector.reduce(
    (sum, row) => sum + Number(row.estimatedRecoverableTax),
    0,
  );
  const collected = filteredRecovery.reduce((sum, row) => sum + Number(row.collectedAmount), 0);
  const openCases = filteredPipeline.reduce((sum, row) => sum + Number(row.caseCount), 0);

  const sectorChart = filteredSector.slice(0, 8).map((row) => ({
    gap: row.estimatedGap,
    recoverable: row.estimatedRecoverableTax,
    sector: row.sectorName,
  }));
  const regionChart = filteredRegion.slice(0, 8).map((row) => ({
    gap: row.estimatedGap,
    recoverable: row.estimatedRecoverableTax,
    region: row.region,
  }));

  const sectorColumns = useMemo<ColumnDef<TaxGapBySectorReport>[]>(
    () => [
      { accessorKey: "sectorName", header: "Sector" },
      { accessorKey: "taxHead", header: "Tax head" },
      { accessorKey: "taxpayerCount", header: "Taxpayers" },
      {
        accessorKey: "estimatedGap",
        cell: ({ row }) => money(row.original.estimatedGap),
        header: "Gap",
      },
      {
        accessorKey: "estimatedRecoverableTax",
        cell: ({ row }) => money(row.original.estimatedRecoverableTax),
        header: "Recoverable",
      },
    ],
    [],
  );
  const pipelineColumns = useMemo<ColumnDef<AuditPipelineReport>[]>(
    () => [
      {
        accessorKey: "status",
        cell: ({ row }) => <Badge value={row.original.status} />,
        header: "Status",
      },
      { accessorKey: "caseCount", header: "Cases" },
      {
        accessorKey: "estimatedRecoverableAmount",
        cell: ({ row }) => money(row.original.estimatedRecoverableAmount),
        header: "Recoverable",
      },
      {
        accessorKey: "collectedAmount",
        cell: ({ row }) => money(row.original.collectedAmount),
        header: "Collected",
      },
    ],
    [],
  );
  const officerColumns = useMemo<ColumnDef<OfficerProductivityReport>[]>(
    () => [
      { accessorKey: "officerName", header: "Officer" },
      { accessorKey: "assignedCases", header: "Assigned" },
      { accessorKey: "openCases", header: "Open" },
      { accessorKey: "closedCases", header: "Closed" },
      {
        accessorKey: "collectedAmount",
        cell: ({ row }) => money(row.original.collectedAmount),
        header: "Collected",
      },
      {
        accessorKey: "averageCaseAgeDays",
        cell: ({ row }) => `${Number(row.original.averageCaseAgeDays).toFixed(1)} days`,
        header: "Avg age",
      },
    ],
    [],
  );

  return (
    <div className="space-y-5">
      <Section
        actions={
          <div className="flex flex-wrap gap-2">
            {(["csv", "xlsx", "pdf"] as const).map((format) => (
              <button
                className="inline-flex min-h-10 items-center gap-2 rounded-md border border-line bg-white px-3 text-sm font-semibold hover:border-authority hover:text-authority disabled:opacity-50"
                disabled={isExporting}
                key={format}
                onClick={() => onExport(format)}
                type="button"
              >
                <FileText size={16} aria-hidden="true" />
                {format.toUpperCase()}
              </button>
            ))}
          </div>
        }
        title="Executive Reports"
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <MetricLine label="Estimated gap" value={money(totalGap)} />
          <MetricLine label="Recoverable tax" value={money(totalRecoverable)} />
          <MetricLine label="Collected" value={money(collected)} />
          <MetricLine label="Pipeline cases" value={String(openCases)} />
        </div>
        {!isLiveSession ? (
          <p className="mt-3 text-sm font-medium text-gray-600">
            Demo figures are shown until a live API session is connected.
          </p>
        ) : null}
      </Section>

      <Section title="Report Filters">
        <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
          <SelectFilter
            label="Tax head"
            onChange={setTaxHeadFilter}
            options={taxHeads}
            value={taxHeadFilter}
          />
          <SearchInput label="Sector" onChange={setSectorFilter} value={sectorFilter} />
          <SearchInput label="Region" onChange={setRegionFilter} value={regionFilter} />
          <SelectFilter
            label="Period"
            onChange={setPeriodFilter}
            options={periods}
            value={periodFilter}
          />
          <SelectFilter
            label="Officer"
            onChange={setOfficerFilter}
            options={officers}
            value={officerFilter}
          />
          <SelectFilter
            label="Status"
            onChange={setStatusFilter}
            options={statuses}
            value={statusFilter}
          />
        </div>
      </Section>

      <div className="grid gap-5 xl:grid-cols-2">
        <Section title="Sector Risk">
          <div className="h-80 min-w-0 overflow-hidden">
            {chartReady && sectorChart.length ? (
              <ResponsiveContainer height="100%" minHeight={320} minWidth={0} width="100%">
                <BarChart data={sectorChart} margin={{ bottom: 8, left: 8, right: 12, top: 12 }}>
                  <CartesianGrid stroke="#d9ded7" vertical={false} />
                  <XAxis dataKey="sector" tickLine={false} />
                  <YAxis tickFormatter={(value) => `${Number(value) / 1_000_000}M`} width={48} />
                  <Tooltip formatter={(value) => money(Number(value))} />
                  <Bar dataKey="gap" fill="#a33b2f" name="Estimated gap" radius={[4, 4, 0, 0]} />
                  <Bar
                    dataKey="recoverable"
                    fill="#245c4f"
                    name="Recoverable"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <EmptyState label="No sector report rows match the filters." />
            )}
          </div>
        </Section>

        <Section title="Regional Risk">
          <div className="h-80 min-w-0 overflow-hidden">
            {chartReady && regionChart.length ? (
              <ResponsiveContainer height="100%" minHeight={320} minWidth={0} width="100%">
                <BarChart data={regionChart} margin={{ bottom: 8, left: 8, right: 12, top: 12 }}>
                  <CartesianGrid stroke="#d9ded7" vertical={false} />
                  <XAxis dataKey="region" tickLine={false} />
                  <YAxis tickFormatter={(value) => `${Number(value) / 1_000_000}M`} width={48} />
                  <Tooltip formatter={(value) => money(Number(value))} />
                  <Bar dataKey="gap" fill="#a33b2f" name="Estimated gap" radius={[4, 4, 0, 0]} />
                  <Bar
                    dataKey="recoverable"
                    fill="#245c4f"
                    name="Recoverable"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <EmptyState label="No regional report rows match the filters." />
            )}
          </div>
        </Section>
      </div>

      <div className="grid gap-5 xl:grid-cols-[1fr_1fr]">
        <Section title="Tax Gap by Sector">
          <DataTable columns={sectorColumns} data={filteredSector} filter="" />
        </Section>
        <Section title="Audit Pipeline">
          <DataTable columns={pipelineColumns} data={filteredPipeline} filter="" />
        </Section>
      </div>

      <Section title="Officer Productivity">
        <DataTable columns={officerColumns} data={filteredOfficers} filter="" />
      </Section>
    </div>
  );
}

function RiskQueueView({
  filter,
  isMutating,
  onCreateCase,
  onFilter,
  onSelectTaxpayer,
  rows,
  selectedSignalId,
  setSelectedSignalId,
}: {
  filter: string;
  isMutating: boolean;
  onCreateCase: (signalId: string) => void;
  onFilter: (value: string) => void;
  onSelectTaxpayer: (taxpayerId: string) => void;
  rows: RiskSignal[];
  selectedSignalId?: string;
  setSelectedSignalId: (signalId: string) => void;
}) {
  const columns = useMemo<ColumnDef<RiskSignal>[]>(
    () => [
      {
        accessorKey: "taxpayerName",
        cell: ({ row }) =>
          row.original.taxpayerId ? (
            <button
              className="text-left font-semibold text-authority hover:underline"
              onClick={() => onSelectTaxpayer(row.original.taxpayerId as string)}
              type="button"
            >
              {row.original.taxpayerName ?? "Unnamed taxpayer"}
            </button>
          ) : (
            <span className="font-semibold text-ink">Public revenue channel</span>
          ),
        header: "Taxpayer",
      },
      { accessorKey: "ruleCode", header: "Rule" },
      { accessorKey: "taxHead", header: "Tax head" },
      {
        accessorKey: "estimatedGap",
        cell: ({ row }) => (
          <span className="font-semibold text-exposure">{money(row.original.estimatedGap)}</span>
        ),
        header: "Gap",
        sortDescFirst: false,
      },
      {
        accessorKey: "confidenceScore",
        cell: ({ row }) => percent(row.original.confidenceScore),
        header: "Confidence",
      },
      {
        accessorKey: "severity",
        cell: ({ row }) => <Badge value={row.original.severity} />,
        header: "Severity",
      },
      {
        cell: ({ row }) => (
          <button
            className="min-h-10 rounded-md bg-authority px-3 text-sm font-semibold text-white hover:bg-[#1d4a40] disabled:opacity-50"
            disabled={isMutating}
            onClick={() => onCreateCase(row.original.id)}
            type="button"
          >
            Open case
          </button>
        ),
        header: "Action",
        id: "action",
      },
    ],
    [isMutating, onCreateCase, onSelectTaxpayer],
  );

  return (
    <Section
      actions={
        <SearchInput
          label="Filter risk queue"
          onChange={onFilter}
          testId="risk-filter"
          value={filter}
        />
      }
      title="Priority Risk Queue"
    >
      <DataTable
        columns={columns}
        data={rows}
        filter={filter}
        getRowClass={(row) => (row.id === selectedSignalId ? "bg-authority/5" : "")}
        onRowClick={(row) => setSelectedSignalId(row.id)}
      />
    </Section>
  );
}

function TaxpayerView({
  graph,
  isGraphPending,
  isLiveSession,
  onSearch,
  onRefreshGraph,
  onSelectTaxpayer,
  profile,
  ranking,
  search,
  selectedTaxpayerId,
}: {
  graph: TaxpayerGraph;
  isGraphPending: boolean;
  isLiveSession: boolean;
  onSearch: (value: string) => void;
  onRefreshGraph: () => void;
  onSelectTaxpayer: (taxpayerId: string) => void;
  profile: TaxpayerProfile;
  ranking: TaxGapRanking[];
  search: string;
  selectedTaxpayerId: string;
}) {
  const filtered = ranking.filter((record) =>
    `${record.taxpayerName} ${record.taxpayerPin}`.toLowerCase().includes(search.toLowerCase()),
  );

  return (
    <div className="grid min-w-0 gap-5 xl:grid-cols-[0.9fr_1.1fr]">
      <Section
        actions={
          <SearchInput
            label="Search taxpayers"
            onChange={onSearch}
            testId="taxpayer-search"
            value={search}
          />
        }
        title="Taxpayer Search"
      >
        <div className="grid gap-2">
          {filtered.map((record) => (
            <button
              className={`rounded-md border p-3 text-left transition ${selectedTaxpayerId === record.taxpayerId ? "border-authority bg-authority/5" : "border-line bg-white hover:border-authority"}`}
              key={record.taxpayerId}
              onClick={() => onSelectTaxpayer(record.taxpayerId)}
              type="button"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="font-semibold">{record.taxpayerName}</p>
                <span className="text-sm font-medium text-gray-700">{record.taxpayerPin}</span>
              </div>
              <p className="mt-2 text-sm text-gray-700">
                Recoverable:{" "}
                <span className="font-semibold text-authority">
                  {money(record.estimatedRecoverableTax)}
                </span>
              </p>
            </button>
          ))}
        </div>
      </Section>

      <Section
        actions={
          <button
            className="inline-flex min-h-10 items-center gap-2 rounded-md border border-authority bg-white px-3 text-sm font-semibold text-authority transition hover:bg-authority/5 disabled:cursor-not-allowed disabled:border-line disabled:text-gray-500"
            disabled={isGraphPending || !isLiveSession}
            onClick={onRefreshGraph}
            title={isLiveSession ? "Refresh graph intelligence" : "Sign in to refresh graph"}
            type="button"
          >
            {isGraphPending ? (
              <Loader2 className="animate-spin" size={16} />
            ) : (
              <Network size={16} />
            )}
            {isGraphPending ? "Refreshing" : "Refresh graph"}
          </button>
        }
        title="Taxpayer Profile"
      >
        <div className="grid gap-4">
          <div>
            <p className="text-sm font-semibold uppercase text-authority">{profile.kraPin}</p>
            <h2 className="mt-1 text-2xl font-semibold">{profile.legalName}</h2>
            <p className="mt-1 text-sm text-gray-700">
              {[profile.taxpayerType, profile.sectorName, profile.county]
                .filter(Boolean)
                .join(" / ")}
            </p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            {Object.entries(profile.totals).map(([key, value]) => (
              <MetricLine key={key} label={key} value={money(value)} />
            ))}
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <InfoList
              items={profile.identifiers.map(
                (item) => `${item.identifierType}: ${item.identifierValue}`,
              )}
              title="Identifiers"
            />
            <InfoList
              items={profile.relationships.map(
                (item) => `${item.relationshipType}: ${item.relatedPersonName}`,
              )}
              title="Relationships"
            />
          </div>
          <GraphPanel graph={graph} rootTaxpayerId={profile.taxpayerId} />
        </div>
      </Section>
    </div>
  );
}

function GraphPanel({ graph, rootTaxpayerId }: { graph: TaxpayerGraph; rootTaxpayerId: string }) {
  const root = graph.nodes.find((node) => node.id === rootTaxpayerId) ?? graph.nodes[0];
  const relatedNodes = graph.nodes.filter((node) => node.id !== root?.id).slice(0, 6);
  const strongestEdges = [...graph.edges]
    .sort((left, right) => Number(right.weight) - Number(left.weight))
    .slice(0, 6);
  const midpoint = Math.ceil(relatedNodes.length / 2);
  const columns = [relatedNodes.slice(0, midpoint), relatedNodes.slice(midpoint)];

  if (!root) {
    return (
      <div className="rounded-md border border-line bg-paper p-4">
        <p className="text-sm font-semibold">Relationship graph</p>
        <p className="mt-2 text-sm text-gray-700">No graph records found for this taxpayer.</p>
      </div>
    );
  }

  return (
    <div className="grid gap-3 xl:grid-cols-[1fr_0.9fr]">
      <div className="rounded-md border border-line bg-paper p-3">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold">Relationship graph</p>
            <p className="text-xs text-gray-600">
              {graph.nodes.length} nodes / {graph.edges.length} edges
            </p>
          </div>
          <Badge value={`${graph.highRiskClusters.length} HIGH RISK`} />
        </div>
        <div className="grid min-h-[320px] grid-cols-1 items-center gap-3 md:grid-cols-[minmax(0,1fr)_minmax(150px,190px)_minmax(0,1fr)]">
          <GraphNodeColumn nodes={columns[0]} />
          <div className="rounded-md border-2 border-authority bg-white p-3 text-center shadow-panel">
            <p className="break-words text-sm font-semibold text-authority">{root.label}</p>
            <p className="mt-2 text-xs font-semibold uppercase text-gray-600">
              {graphTypeLabel(root.nodeType)}
            </p>
            <p className="mt-3 text-2xl font-semibold">{percent(root.riskScore)}</p>
            <p className="text-xs text-gray-600">risk score</p>
          </div>
          <GraphNodeColumn nodes={columns[1]} />
        </div>
      </div>

      <div className="grid gap-3">
        <div className="rounded-md border border-line bg-paper p-3">
          <h3 className="text-sm font-semibold">Strongest links</h3>
          <div className="mt-3 grid gap-2">
            {strongestEdges.length ? (
              strongestEdges.map((edge) => <GraphEdgeItem edge={edge} key={edge.id} />)
            ) : (
              <p className="text-sm text-gray-700">No relationship edges found.</p>
            )}
          </div>
        </div>
        <div className="rounded-md border border-line bg-paper p-3">
          <h3 className="text-sm font-semibold">High-risk clusters</h3>
          <div className="mt-3 grid gap-2">
            {graph.highRiskClusters.length ? (
              graph.highRiskClusters.slice(0, 3).map((cluster) => (
                <div
                  className="rounded-md border border-exposure/20 bg-white p-3"
                  key={cluster.clusterKey}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="break-words text-sm font-semibold">
                      {cluster.sourceTaxpayerName} / {cluster.targetTaxpayerName}
                    </p>
                    <Badge value={graphTypeLabel(cluster.edgeType)} />
                  </div>
                  <p className="mt-2 text-xs text-gray-700">{cluster.reasons.join(" / ")}</p>
                </div>
              ))
            ) : (
              <p className="text-sm text-gray-700">No high-risk clusters found.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function GraphNodeColumn({ nodes }: { nodes: TaxpayerGraph["nodes"] }) {
  return (
    <div className="grid gap-2">
      {nodes.length ? (
        nodes.map((node) => (
          <div className="rounded-md border border-line bg-white p-3 shadow-panel" key={node.id}>
            <p className="break-words text-sm font-semibold">{node.label}</p>
            <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
              <span className="text-xs font-semibold uppercase text-gray-600">
                {graphTypeLabel(node.nodeType)}
              </span>
              <span className="text-sm font-semibold text-authority">
                {percent(node.riskScore)}
              </span>
            </div>
          </div>
        ))
      ) : (
        <div className="rounded-md border border-dashed border-line bg-white p-3 text-sm text-gray-700">
          No related nodes
        </div>
      )}
    </div>
  );
}

function GraphEdgeItem({ edge }: { edge: GraphEdge }) {
  return (
    <div className="rounded-md border border-line bg-white p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="break-words text-sm font-semibold">
          {edge.sourceLabel} / {edge.targetLabel}
        </p>
        <span className="text-sm font-semibold text-authority">
          {Number(edge.weight).toFixed(1)}
        </span>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <Badge value={graphTypeLabel(edge.edgeType)} />
        <span className="text-xs font-semibold uppercase text-gray-600">
          {graphTypeLabel(edge.source)}
        </span>
      </div>
    </div>
  );
}

function CasesView({
  caseDetail,
  filter,
  isEvidencePending,
  isNotePending,
  note,
  onAddNote,
  onFilter,
  onGenerateEvidence,
  onNote,
  onSelectCase,
  rows,
  selectedCaseId,
}: {
  caseDetail: CaseDetail;
  filter: string;
  isEvidencePending: boolean;
  isNotePending: boolean;
  note: string;
  onAddNote: () => void;
  onFilter: (value: string) => void;
  onGenerateEvidence: () => void;
  onNote: (value: string) => void;
  onSelectCase: (caseId: string) => void;
  rows: CaseRecord[];
  selectedCaseId?: string;
}) {
  const columns = useMemo<ColumnDef<CaseRecord>[]>(
    () => [
      { accessorKey: "caseNumber", header: "Case" },
      {
        accessorKey: "taxpayerName",
        cell: ({ row }) => row.original.taxpayerName ?? "Public revenue channel",
        header: "Taxpayer",
      },
      {
        accessorKey: "priority",
        cell: ({ row }) => <Badge value={row.original.priority} />,
        header: "Priority",
      },
      {
        accessorKey: "status",
        cell: ({ row }) => <Badge value={row.original.status} />,
        header: "Status",
      },
      {
        accessorKey: "estimatedRecoverableAmount",
        cell: ({ row }) => money(row.original.estimatedRecoverableAmount),
        header: "Recoverable",
      },
      { accessorKey: "assignedOfficerName", header: "Officer" },
    ],
    [],
  );

  return (
    <div className="grid min-w-0 gap-5 xl:grid-cols-[1.05fr_0.95fr]">
      <Section
        actions={
          <SearchInput
            label="Filter cases"
            onChange={onFilter}
            testId="case-filter"
            value={filter}
          />
        }
        title="Case List"
      >
        <DataTable
          columns={columns}
          data={rows}
          filter={filter}
          getRowClass={(row) => (row.id === selectedCaseId ? "bg-authority/5" : "")}
          onRowClick={(row) => onSelectCase(row.id)}
        />
      </Section>

      <Section title="Case Detail">
        <div className="space-y-4">
          <div>
            <p className="text-sm font-semibold uppercase text-authority">
              {caseDetail.detail.caseNumber}
            </p>
            <h2 className="mt-1 text-xl font-semibold">{caseDetail.detail.title}</h2>
            <p className="mt-2 text-sm text-gray-700">
              {caseDetail.detail.taxpayerPin ?? "No taxpayer PIN"} /{" "}
              {caseDetail.detail.taxpayerName ?? "Public revenue channel"}
            </p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <MetricLine label="Status" value={caseDetail.detail.status} />
            <MetricLine
              label="Recoverable"
              value={money(caseDetail.detail.estimatedRecoverableAmount)}
            />
            <MetricLine label="Assessed" value={money(caseDetail.detail.assessedAmount)} />
            <MetricLine label="Collected" value={money(caseDetail.detail.collectedAmount)} />
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              className="inline-flex min-h-11 items-center gap-2 rounded-md bg-authority px-4 text-sm font-semibold text-white hover:bg-[#1d4a40] disabled:opacity-50"
              disabled={isEvidencePending}
              onClick={onGenerateEvidence}
              type="button"
            >
              {isEvidencePending ? (
                <Loader2 className="animate-spin" size={17} aria-hidden="true" />
              ) : (
                <FileText size={17} aria-hidden="true" />
              )}
              Generate evidence
            </button>
          </div>
          <div>
            <label className="text-sm font-semibold" htmlFor="case-note">
              Case note
            </label>
            <textarea
              className="mt-2 min-h-24 w-full rounded-md border border-line bg-white p-3 text-sm"
              id="case-note"
              onChange={(event) => onNote(event.target.value)}
              value={note}
            />
            <button
              data-testid="add-case-note"
              className="mt-2 min-h-10 rounded-md border border-line bg-white px-3 text-sm font-semibold hover:border-authority hover:text-authority disabled:opacity-50"
              disabled={isNotePending || !note.trim()}
              onClick={onAddNote}
              type="button"
            >
              Add note
            </button>
          </div>
          <InfoList
            items={caseDetail.events.map((event) => `${event.eventType}: ${event.note}`)}
            title="Events"
          />
        </div>
      </Section>
    </div>
  );
}

function EvidenceView({ caseDetail, token }: { caseDetail: CaseDetail; token: string | null }) {
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const latestPack = caseDetail.evidencePacks[0] ?? demoCaseDetail.evidencePacks[0];
  const evidence = latestPack.evidence ?? latestPack.content ?? {};
  const packBelongsToCase = latestPack.caseId === caseDetail.detail.id;
  const canDownload = Boolean(
    token &&
      packBelongsToCase &&
      latestPack.fileUri?.startsWith("file:") &&
      latestPack.id &&
      caseDetail.detail.id,
  );

  async function handleOpenPdf() {
    if (!token || !canDownload) {
      return;
    }

    setDownloadError(null);
    setIsDownloading(true);
    try {
      const blob = await downloadEvidencePackPdf(token, caseDetail.detail.id, latestPack.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `${caseDetail.detail.caseNumber}-evidence-pack-v${latestPack.version}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      setDownloadError(error instanceof Error ? error.message : "PDF export failed");
    } finally {
      setIsDownloading(false);
    }
  }

  return (
    <Section title="Evidence Pack Viewer">
      <div className="grid gap-5 xl:grid-cols-[0.8fr_1.2fr]">
        <div className="space-y-3">
          <MetricLine label="Case" value={caseDetail.detail.caseNumber} />
          <MetricLine
            label="Taxpayer"
            value={caseDetail.detail.taxpayerName ?? "Public revenue channel"}
          />
          <MetricLine label="Version" value={String(latestPack.version)} />
          <MetricLine label="Generated" value={shortDate(latestPack.generatedAt)} />
          <button
            className={`inline-flex min-h-11 items-center gap-2 rounded-md px-4 text-sm font-semibold ${canDownload ? "bg-authority text-white hover:bg-[#1d4a40]" : "bg-line text-gray-700"}`}
            disabled={!canDownload || isDownloading}
            onClick={handleOpenPdf}
            type="button"
          >
            {isDownloading ? (
              <Loader2 className="animate-spin" size={17} aria-hidden="true" />
            ) : (
              <FileText size={17} aria-hidden="true" />
            )}
            {isDownloading ? "Preparing PDF" : "Download PDF"}
          </button>
          {downloadError ? (
            <p className="text-sm font-medium text-exposure">{downloadError}</p>
          ) : null}
        </div>
        <pre className="max-h-[560px] overflow-auto rounded-md border border-line bg-[#111827] p-4 text-xs leading-5 text-white">
          {JSON.stringify(evidence, null, 2)}
        </pre>
      </div>
    </Section>
  );
}

function SettlementView({
  isCasePending,
  isRunPending,
  onOpenCase,
  onRun,
  results,
  summary,
}: {
  isCasePending: boolean;
  isRunPending: boolean;
  onOpenCase: (resultId: string) => void;
  onRun: () => void;
  results: ReconciliationResult[];
  summary: ReconciliationSummary;
}) {
  const columns = useMemo<ColumnDef<ReconciliationResult>[]>(
    () => [
      {
        accessorKey: "reconciliationDate",
        cell: ({ row }) => shortDate(row.original.reconciliationDate),
        header: "Date",
      },
      { accessorKey: "collectingAgency", header: "Agency" },
      { accessorKey: "revenueChannel", header: "Channel" },
      {
        accessorKey: "expectedAmount",
        cell: ({ row }) => money(row.original.expectedAmount),
        header: "Collections",
      },
      {
        accessorKey: "settledAmount",
        cell: ({ row }) => money(row.original.settledAmount),
        header: "Settled",
      },
      {
        accessorKey: "varianceAmount",
        cell: ({ row }) => (
          <span className={row.original.varianceAmount > 0 ? "font-semibold text-exposure" : ""}>
            {money(row.original.varianceAmount)}
          </span>
        ),
        header: "Variance",
      },
      {
        accessorKey: "settlementStatus",
        cell: ({ row }) => <Badge value={row.original.settlementStatus} />,
        header: "Status",
      },
      {
        id: "case",
        cell: ({ row }) =>
          row.original.settlementStatus === "MATCHED" ? (
            <span className="text-xs font-semibold text-gray-500">Clear</span>
          ) : (
            <button
              className="inline-flex min-h-9 items-center gap-2 rounded-md bg-authority px-3 text-xs font-semibold text-white hover:bg-[#1d4a40]"
              disabled={isCasePending || !row.original.riskSignalId}
              onClick={(event) => {
                event.stopPropagation();
                onOpenCase(row.original.id);
              }}
              type="button"
            >
              {isCasePending ? (
                <Loader2 className="animate-spin" size={15} aria-hidden="true" />
              ) : (
                <BriefcaseBusiness size={15} aria-hidden="true" />
              )}
              Case
            </button>
          ),
        header: "Action",
      },
    ],
    [isCasePending, onOpenCase],
  );

  return (
    <div className="grid min-w-0 gap-5">
      <Section
        actions={
          <button
            className="inline-flex min-h-11 items-center gap-2 rounded-md bg-authority px-4 text-sm font-semibold text-white hover:bg-[#1d4a40]"
            disabled={isRunPending}
            onClick={onRun}
            type="button"
          >
            {isRunPending ? (
              <Loader2 className="animate-spin" size={17} aria-hidden="true" />
            ) : (
              <BadgeCheck size={17} aria-hidden="true" />
            )}
            Run reconciliation
          </button>
        }
        title="Settlement Monitor"
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <article className="rounded-md border border-line bg-white p-4 shadow-panel">
            <p className="text-sm font-medium text-gray-700">Collections</p>
            <p className="mt-3 text-2xl font-semibold text-authority">
              {money(summary.expectedAmount)}
            </p>
          </article>
          <article className="rounded-md border border-line bg-white p-4 shadow-panel">
            <p className="text-sm font-medium text-gray-700">Settlements</p>
            <p className="mt-3 text-2xl font-semibold text-assurance">
              {money(summary.settledAmount)}
            </p>
          </article>
          <article className="rounded-md border border-line bg-white p-4 shadow-panel">
            <p className="text-sm font-medium text-gray-700">Variance</p>
            <p className="mt-3 text-2xl font-semibold text-exposure">
              {money(summary.varianceAmount)}
            </p>
          </article>
          <article className="rounded-md border border-line bg-white p-4 shadow-panel">
            <p className="text-sm font-medium text-gray-700">Exceptions</p>
            <p className="mt-3 text-2xl font-semibold text-revenue">{summary.exceptionCount}</p>
          </article>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <FlowItem
            icon={<AlertTriangle size={20} />}
            label="Missing settlements"
            value={String(summary.missingCount)}
          />
          <FlowItem
            icon={<Activity size={20} />}
            label="Delayed settlements"
            value={String(summary.delayedCount)}
          />
          <FlowItem
            icon={<ArrowDownUp size={20} />}
            label="Duplicate payments"
            value={String(summary.duplicateCount)}
          />
          <FlowItem
            icon={<ShieldCheck size={20} />}
            label="Wrong account"
            value={String(summary.wrongAccountCount)}
          />
        </div>
      </Section>

      <Section title="Exception Report">
        <DataTable columns={columns} data={results} filter="" />
      </Section>
    </div>
  );
}

function NotificationView({
  cases,
  isCasePending,
  isResponsePending,
  isRiskPending,
  notifications,
  onRecordResponse,
  onResponseBody,
  onResponseStatus,
  onSelectCase,
  onSelectSignal,
  onSendCaseNudge,
  onSendRiskNudge,
  responseBody,
  responseStatus,
  riskSignals,
  selectedCaseId,
  selectedSignalId,
  templates,
}: {
  cases: CaseRecord[];
  isCasePending: boolean;
  isResponsePending: boolean;
  isRiskPending: boolean;
  notifications: NotificationRecord[];
  onRecordResponse: (notificationId: string) => void;
  onResponseBody: (value: string) => void;
  onResponseStatus: (value: string) => void;
  onSelectCase: (caseId: string) => void;
  onSelectSignal: (signalId: string) => void;
  onSendCaseNudge: (channel: string) => void;
  onSendRiskNudge: (channel: string) => void;
  responseBody: string;
  responseStatus: string;
  riskSignals: RiskSignal[];
  selectedCaseId?: string;
  selectedSignalId?: string;
  templates: NotificationTemplate[];
}) {
  const sortedNotifications = useMemo(
    () =>
      [...notifications].sort(
        (left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
      ),
    [notifications],
  );
  const [selectedNotificationId, setSelectedNotificationId] = useState(
    sortedNotifications[0]?.id ?? "",
  );
  const selectedNotification =
    sortedNotifications.find((record) => record.id === selectedNotificationId) ??
    sortedNotifications[0];

  useEffect(() => {
    if (!sortedNotifications.length) {
      setSelectedNotificationId("");
      return;
    }

    if (!sortedNotifications.some((record) => record.id === selectedNotificationId)) {
      setSelectedNotificationId(sortedNotifications[0].id);
    }
  }, [selectedNotificationId, sortedNotifications]);

  const columns = useMemo<ColumnDef<NotificationRecord>[]>(
    () => [
      {
        accessorKey: "createdAt",
        cell: ({ row }) => shortDate(row.original.createdAt),
        header: "Sent",
      },
      {
        accessorKey: "channel",
        cell: ({ row }) => <Badge value={row.original.channel} />,
        header: "Channel",
      },
      { accessorKey: "templateCode", header: "Template" },
      { accessorKey: "recipient", header: "Recipient" },
      {
        accessorKey: "status",
        cell: ({ row }) => <Badge value={row.original.status} />,
        header: "Status",
      },
      {
        accessorKey: "responseStatus",
        cell: ({ row }) => row.original.responseStatus ?? "-",
        header: "Response",
      },
    ],
    [],
  );

  return (
    <div className="grid min-w-0 gap-5">
      <div className="grid gap-5 xl:grid-cols-2">
        <Section title="Generate Nudge">
          <div className="grid gap-4">
            <label className="grid gap-2 text-sm font-semibold" htmlFor="nudge-case">
              Case
              <select
                className="min-h-11 rounded-md border border-line bg-white px-3 text-sm"
                id="nudge-case"
                onChange={(event) => onSelectCase(event.target.value)}
                value={selectedCaseId ?? ""}
              >
                {cases.map((record) => (
                  <option key={record.id} value={record.id}>
                    {record.caseNumber} / {record.taxpayerName ?? "Public revenue channel"}
                  </option>
                ))}
              </select>
            </label>
            <div className="flex flex-wrap gap-2">
              <NudgeButton
                channel="EMAIL"
                disabled={isCasePending}
                icon={<Mail size={17} aria-hidden="true" />}
                isPending={isCasePending}
                onClick={onSendCaseNudge}
              />
              <NudgeButton
                channel="SMS"
                disabled={isCasePending}
                icon={<Send size={17} aria-hidden="true" />}
                isPending={isCasePending}
                onClick={onSendCaseNudge}
              />
            </div>
            <label className="grid gap-2 text-sm font-semibold" htmlFor="nudge-signal">
              Risk signal
              <select
                className="min-h-11 rounded-md border border-line bg-white px-3 text-sm"
                id="nudge-signal"
                onChange={(event) => onSelectSignal(event.target.value)}
                value={selectedSignalId ?? ""}
              >
                {riskSignals.map((signal) => (
                  <option key={signal.id} value={signal.id}>
                    {signal.ruleCode} / {signal.taxpayerName ?? "Public revenue channel"}
                  </option>
                ))}
              </select>
            </label>
            <div className="flex flex-wrap gap-2">
              <NudgeButton
                channel="EMAIL"
                disabled={isRiskPending}
                icon={<Mail size={17} aria-hidden="true" />}
                isPending={isRiskPending}
                onClick={onSendRiskNudge}
              />
              <NudgeButton
                channel="SMS"
                disabled={isRiskPending}
                icon={<Send size={17} aria-hidden="true" />}
                isPending={isRiskPending}
                onClick={onSendRiskNudge}
              />
            </div>
          </div>
        </Section>

        <Section title="Taxpayer Response">
          <div className="grid gap-3">
            <label className="grid gap-2 text-sm font-semibold" htmlFor="response-notification">
              Notification
              <select
                className="min-h-11 rounded-md border border-line bg-white px-3 text-sm"
                disabled={!sortedNotifications.length}
                id="response-notification"
                onChange={(event) => setSelectedNotificationId(event.target.value)}
                value={selectedNotification?.id ?? ""}
              >
                {sortedNotifications.map((record) => (
                  <option key={record.id} value={record.id}>
                    {record.templateCode} / {record.recipient}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-2 text-sm font-semibold" htmlFor="response-status">
              Response status
              <select
                className="min-h-11 rounded-md border border-line bg-white px-3 text-sm"
                id="response-status"
                onChange={(event) => onResponseStatus(event.target.value)}
                value={responseStatus}
              >
                <option value="DOCUMENTS_SUBMITTED">Documents submitted</option>
                <option value="RETURN_AMENDED">Return amended</option>
                <option value="PAYMENT_PLAN_REQUESTED">Payment plan requested</option>
                <option value="DISPUTED">Disputed</option>
              </select>
            </label>
            <label className="grid gap-2 text-sm font-semibold" htmlFor="response-body">
              Response note
              <textarea
                className="min-h-24 rounded-md border border-line bg-white p-3 text-sm"
                id="response-body"
                onChange={(event) => onResponseBody(event.target.value)}
                value={responseBody}
              />
            </label>
            <button
              className="inline-flex min-h-11 w-fit items-center gap-2 rounded-md bg-authority px-4 text-sm font-semibold text-white hover:bg-[#1d4a40] disabled:opacity-50"
              disabled={isResponsePending || !selectedNotification || !responseBody.trim()}
              onClick={() => selectedNotification && onRecordResponse(selectedNotification.id)}
              type="button"
            >
              {isResponsePending ? (
                <Loader2 className="animate-spin" size={17} aria-hidden="true" />
              ) : (
                <MessageSquareReply size={17} aria-hidden="true" />
              )}
              Record response
            </button>
          </div>
        </Section>
      </div>

      <Section title="Template Library">
        <div className="grid gap-3 md:grid-cols-3">
          {templates.map((template) => (
            <article
              className="rounded-md border border-line bg-white p-4 shadow-panel"
              key={template.id}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold">{template.code}</h3>
                <Badge value={template.channel} />
              </div>
              <p className="mt-3 text-sm leading-6 text-gray-700">{template.bodyTemplate}</p>
            </article>
          ))}
        </div>
      </Section>

      <Section title="Communication History">
        <DataTable
          columns={columns}
          data={sortedNotifications}
          filter=""
          getRowClass={(row) => (row.id === selectedNotification?.id ? "bg-authority/5" : "")}
          onRowClick={(row) => setSelectedNotificationId(row.id)}
        />
      </Section>
    </div>
  );
}

function NudgeButton({
  channel,
  disabled,
  icon,
  isPending,
  onClick,
}: {
  channel: string;
  disabled: boolean;
  icon: ReactNode;
  isPending: boolean;
  onClick: (channel: string) => void;
}) {
  return (
    <button
      className="inline-flex min-h-11 items-center gap-2 rounded-md bg-authority px-4 text-sm font-semibold text-white hover:bg-[#1d4a40] disabled:opacity-50"
      disabled={disabled}
      onClick={() => onClick(channel)}
      type="button"
    >
      {isPending ? <Loader2 className="animate-spin" size={17} aria-hidden="true" /> : icon}
      {channel}
    </button>
  );
}

function AiScoringView({
  dashboard,
  isLiveSession,
  isRunPending,
  modelVersions,
  onRun,
  onSelectTaxpayer,
  predictions,
  runError,
}: {
  dashboard: RiskScoringDashboard;
  isLiveSession: boolean;
  isRunPending: boolean;
  modelVersions: ModelVersion[];
  onRun: () => void;
  onSelectTaxpayer: (taxpayerId: string) => void;
  predictions: ModelPrediction[];
  runError: unknown;
}) {
  const [chartReady, setChartReady] = useState(false);
  const topPredictions = dashboard.topPredictions.length
    ? dashboard.topPredictions
    : predictions.slice(0, 10);
  const activeVersion = modelVersions.find((version) => version.active) ?? modelVersions[0] ?? null;
  const chartData = topPredictions.slice(0, 6).map((prediction) => ({
    combined: Number(prediction.explanation.combinedScore ?? prediction.score),
    model: Number(prediction.score),
    taxpayer: prediction.legalName.split(" ").slice(0, 2).join(" "),
  }));
  const leadingFactors = contributionRows(topPredictions[0]);
  const runErrorMessage = runError ? scoringErrorMessage(runError) : null;
  const statusMessage = isLiveSession
    ? predictions.length
      ? "Live model output is available."
      : "Live API is connected. Load taxpayer records, then run scoring to train the Phase 12 Python model."
    : "Demo preview is active. Sign in to run scoring against the live backend.";

  useEffect(() => {
    setChartReady(true);
  }, []);

  const columns = useMemo<ColumnDef<ModelPrediction>[]>(
    () => [
      {
        accessorKey: "legalName",
        cell: ({ row }) => (
          <button
            className="text-left font-semibold text-authority hover:underline"
            onClick={() => onSelectTaxpayer(row.original.taxpayerId)}
            type="button"
          >
            {row.original.legalName}
          </button>
        ),
        header: "Taxpayer",
      },
      { accessorKey: "kraPin", header: "PIN" },
      {
        accessorKey: "score",
        cell: ({ row }) => (
          <span className="font-semibold text-exposure">{percent(row.original.score)}</span>
        ),
        header: "Model score",
      },
      {
        accessorKey: "combinedScore",
        cell: ({ row }) => percent(Number(row.original.explanation.combinedScore ?? 0)),
        header: "Combined score",
      },
      {
        accessorKey: "mainContributingFeatures",
        cell: ({ row }) =>
          (row.original.explanation.mainContributingFeatures ?? [])
            .slice(0, 2)
            .map(featureLabel)
            .join(", ") || "-",
        header: "Main factors",
      },
      {
        accessorKey: "createdAt",
        cell: ({ row }) => shortDate(row.original.createdAt),
        header: "Scored",
      },
    ],
    [onSelectTaxpayer],
  );

  return (
    <div className="grid min-w-0 gap-5">
      <Section
        actions={
          <button
            className="inline-flex min-h-11 items-center gap-2 rounded-md bg-authority px-4 text-sm font-semibold text-white hover:bg-[#1d4a40] disabled:opacity-50"
            disabled={isRunPending || !isLiveSession}
            onClick={onRun}
            type="button"
          >
            {isRunPending ? (
              <Loader2 className="animate-spin" size={17} aria-hidden="true" />
            ) : (
              <BrainCircuit size={17} aria-hidden="true" />
            )}
            {isLiveSession ? "Run scoring" : "Sign in to run"}
          </button>
        }
        title="AI Risk Scoring"
      >
        <div
          className={`mb-4 rounded-md border p-3 text-sm font-medium ${
            runErrorMessage
              ? "border-exposure/30 bg-exposure/5 text-exposure"
              : isLiveSession
                ? "border-assurance/30 bg-assurance/10 text-assurance"
                : "border-revenue/30 bg-revenue/10 text-revenue"
          }`}
        >
          {runErrorMessage ?? statusMessage}
        </div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          <MetricLine label="Active model" value={dashboard.activeModelVersion} />
          <MetricLine label="Predictions" value={String(dashboard.predictionCount)} />
          <MetricLine label="High risk" value={String(dashboard.highRiskCount)} />
          <MetricLine label="Avg model score" value={percent(dashboard.averageModelScore)} />
          <MetricLine label="Avg combined score" value={percent(dashboard.averageCombinedScore)} />
        </div>
      </Section>

      <div className="grid gap-5 xl:grid-cols-[1.2fr_0.8fr]">
        <Section title="Top Prediction Scores">
          <div className="h-80 min-w-0 overflow-hidden">
            {chartReady && chartData.length ? (
              <ResponsiveContainer height="100%" minHeight={320} minWidth={0} width="100%">
                <BarChart data={chartData} margin={{ bottom: 8, left: 8, right: 12, top: 12 }}>
                  <CartesianGrid stroke="#d9ded7" vertical={false} />
                  <XAxis dataKey="taxpayer" tickLine={false} />
                  <YAxis domain={[0, 100]} tickFormatter={(value) => `${value}%`} width={44} />
                  <Tooltip formatter={(value) => percent(Number(value))} />
                  <Bar dataKey="model" fill="#a33b2f" name="Model score" radius={[4, 4, 0, 0]} />
                  <Bar
                    dataKey="combined"
                    fill="#245c4f"
                    name="Combined score"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-full items-center justify-center rounded-md border border-line bg-paper px-4 text-center text-sm font-medium text-gray-700">
                {isLiveSession
                  ? "No live model predictions yet."
                  : "Demo predictions appear after the preview data loads."}
              </div>
            )}
          </div>
        </Section>

        <Section title="Explainability Snapshot">
          <div className="grid gap-3">
            {leadingFactors.length ? (
              leadingFactors.map(([label, value]) => (
                <div className="rounded-md border border-line bg-paper p-3" key={label}>
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-sm font-semibold">{featureLabel(label)}</p>
                    <p className="text-sm font-semibold text-authority">{percent(value)}</p>
                  </div>
                  <div className="mt-3 h-2 rounded-full bg-white">
                    <div
                      className="h-2 rounded-full bg-authority"
                      style={{ width: `${Math.max(0, Math.min(100, Number(value)))}%` }}
                    />
                  </div>
                </div>
              ))
            ) : (
              <p className="text-sm text-gray-700">No model explanations yet.</p>
            )}
            <Badge
              value={
                topPredictions[0]?.explanation.officerReviewRequired ? "OFFICER_REVIEW" : "REVIEW"
              }
            />
          </div>
        </Section>
      </div>

      <Section title="Prediction Queue">
        <DataTable
          columns={columns}
          data={predictions}
          filter=""
          getRowClass={(row) =>
            Number(row.explanation.combinedScore ?? row.score) >= 70 ? "bg-exposure/5" : ""
          }
          onRowClick={(row) => onSelectTaxpayer(row.taxpayerId)}
        />
      </Section>

      <Section title="Model Versions">
        <div className="grid gap-3">
          {modelVersions.map((version) => (
            <article
              className="rounded-md border border-line bg-white p-4 shadow-panel"
              key={version.id}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold uppercase text-authority">
                    {version.modelName}
                  </p>
                  <h3 className="mt-1 text-lg font-semibold">{version.version}</h3>
                  <p className="mt-1 text-sm leading-6 text-gray-700">
                    {version.trainingDataSummary}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Badge value={version.active ? "ACTIVE" : "INACTIVE"} />
                  <Badge value={version.modelType} />
                </div>
              </div>
              <pre className="mt-3 overflow-auto rounded-md bg-paper p-3 text-xs leading-5">
                {JSON.stringify(version.metrics, null, 2)}
              </pre>
            </article>
          ))}
          {!modelVersions.length && (
            <p className="rounded-md border border-line bg-paper p-3 text-sm text-gray-700">
              No model versions have been stored yet.
            </p>
          )}
          {activeVersion ? (
            <p className="text-sm text-gray-700">Latest active version: {activeVersion.version}</p>
          ) : null}
        </div>
      </Section>
    </div>
  );
}

function IngestionView({ dataSources, jobs }: { dataSources: DataSource[]; jobs: IngestionJob[] }) {
  return (
    <div className="grid gap-5 xl:grid-cols-[0.75fr_1.25fr]">
      <Section title="Data Sources">
        <div className="grid gap-3">
          {dataSources.map((source) => (
            <article
              className="rounded-md border border-line bg-white p-4 shadow-panel"
              key={source.id}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold">{source.name}</h3>
                <Badge value={source.active ? "ACTIVE" : "INACTIVE"} />
              </div>
              <p className="mt-2 text-sm text-gray-700">
                {source.code} / {source.ownerAgency} / {source.schemaVersion}
              </p>
            </article>
          ))}
        </div>
      </Section>
      <Section title="Ingestion Status">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] border-collapse text-left text-sm">
            <thead className="bg-paper text-gray-700">
              <tr>
                <th className="px-4 py-3 font-semibold">File</th>
                <th className="px-4 py-3 font-semibold">Target</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                <th className="px-4 py-3 font-semibold">Valid</th>
                <th className="px-4 py-3 font-semibold">Invalid</th>
                <th className="px-4 py-3 font-semibold">Started</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr className="border-t border-line" key={job.id}>
                  <td className="px-4 py-3 font-medium">{job.fileName}</td>
                  <td className="px-4 py-3">{job.targetTable}</td>
                  <td className="px-4 py-3">
                    <Badge value={job.status} />
                  </td>
                  <td className="px-4 py-3">{job.recordsValid.toLocaleString()}</td>
                  <td className="px-4 py-3">{job.recordsInvalid.toLocaleString()}</td>
                  <td className="px-4 py-3">{shortDate(job.startedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>
    </div>
  );
}

function GovernanceView({
  dashboard,
  isAdmin,
}: {
  dashboard: AdminGovernanceDashboard;
  isAdmin: boolean;
}) {
  const overview = dashboard.overview;
  return (
    <div className="grid gap-5">
      <Section
        actions={
          <span
            className={`inline-flex min-h-10 items-center gap-2 rounded-md border px-3 text-sm font-semibold ${isAdmin ? "border-assurance/30 bg-assurance/10 text-assurance" : "border-revenue/30 bg-revenue/10 text-revenue"}`}
          >
            {isAdmin ? (
              <ShieldCheck size={17} aria-hidden="true" />
            ) : (
              <Lock size={17} aria-hidden="true" />
            )}
            {isAdmin ? "Admin controlled" : "Admin only"}
          </span>
        }
        title="Administration Governance"
      >
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <MetricLine label="Roles" value={overview.rolesCount.toLocaleString()} />
          <MetricLine label="Permissions" value={overview.permissionsCount.toLocaleString()} />
          <MetricLine
            label="Role mappings"
            value={overview.rolePermissionMappingsCount.toLocaleString()}
          />
          <MetricLine
            label="Privacy complete"
            value={`${overview.privacyChecklistCompletedCount}/${overview.privacyChecklistCount}`}
          />
          <MetricLine label="Data sources" value={overview.dataSourcesCount.toLocaleString()} />
          <MetricLine label="Rules" value={overview.riskRulesCount.toLocaleString()} />
          <MetricLine label="Model versions" value={overview.modelVersionsCount.toLocaleString()} />
          <MetricLine
            label="Retention policies"
            value={overview.activeRetentionPoliciesCount.toLocaleString()}
          />
        </div>
      </Section>

      <div className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
        <Section title="Role Permissions">
          <div className="grid gap-3">
            {dashboard.rolePermissions.map((role) => (
              <article
                className="rounded-md border border-line bg-white p-4 shadow-panel"
                key={role.roleCode}
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <h3 className="font-semibold">{role.roleName}</h3>
                    <p className="text-sm text-gray-600">{role.roleCode}</p>
                  </div>
                  <Badge value={`${role.permissions.length} permissions`} />
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {role.permissions.map((permission) => (
                    <span
                      className="inline-flex min-h-7 items-center rounded-md border border-line bg-paper px-2 text-xs font-semibold text-gray-700"
                      key={`${role.roleCode}-${permission}`}
                    >
                      {permission.replaceAll("_", " ")}
                    </span>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </Section>

        <Section title="Export And MFA Controls">
          <div className="grid gap-3">
            <article className="rounded-md border border-line bg-white p-4 shadow-panel">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold">Sensitive Export</h3>
                <Badge value={dashboard.exportControls.requiredPermission} />
              </div>
              <p className="mt-2 text-sm text-gray-700">{dashboard.exportControls.policy}</p>
              <p className="mt-3 text-sm font-semibold">
                Roles: {dashboard.exportControls.allowedRoles.join(", ")}
              </p>
            </article>
            <article className="rounded-md border border-line bg-white p-4 shadow-panel">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold">{dashboard.keycloakMfa.provider} MFA</h3>
                <Badge value={dashboard.keycloakMfa.status} />
              </div>
              <p className="mt-2 text-sm text-gray-700">
                {dashboard.keycloakMfa.configurationPath}
              </p>
              <p className="mt-3 text-sm font-semibold">
                Privileged roles: {dashboard.keycloakMfa.pilotRoles.join(", ")}
              </p>
            </article>
          </div>
        </Section>
      </div>

      <div className="grid gap-5 xl:grid-cols-2">
        <Section title="Retention Policies">
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead>
                <tr className="text-xs uppercase text-gray-600">
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Days</th>
                  <th className="px-4 py-3">Status</th>
                </tr>
              </thead>
              <tbody>
                {dashboard.retentionPolicies.map((policy) => (
                  <tr className="border-t border-line" key={policy.id}>
                    <td className="px-4 py-3 font-medium">{policy.dataCategory}</td>
                    <td className="px-4 py-3">{policy.retentionDays.toLocaleString()}</td>
                    <td className="px-4 py-3">
                      <Badge value={policy.active ? "ACTIVE" : "INACTIVE"} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Section>

        <Section title="Privacy Checklist">
          <div className="grid gap-3">
            {dashboard.privacyImpactChecklist.map((item) => (
              <article
                className="rounded-md border border-line bg-white p-4 shadow-panel"
                key={item.id}
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="font-semibold">{item.dataCategory}</h3>
                  <Badge value={item.completed ? "COMPLETED" : "OPEN"} />
                </div>
                <p className="mt-2 text-sm text-gray-700">{item.purpose}</p>
                <p className="mt-2 text-xs font-semibold uppercase text-gray-600">
                  {item.maskingRequired ? "Masking required" : "Operational log"}
                </p>
              </article>
            ))}
          </div>
        </Section>
      </div>

      <Section title="Recent Audit Events">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead>
              <tr className="text-xs uppercase text-gray-600">
                <th className="px-4 py-3">Action</th>
                <th className="px-4 py-3">Actor</th>
                <th className="px-4 py-3">Entity</th>
                <th className="px-4 py-3">Time</th>
              </tr>
            </thead>
            <tbody>
              {dashboard.auditLogs.map((log) => (
                <tr className="border-t border-line" key={log.id}>
                  <td className="px-4 py-3">
                    <Badge value={log.action} />
                  </td>
                  <td className="px-4 py-3">{log.actorEmail ?? "system"}</td>
                  <td className="px-4 py-3">{log.entityType ?? "-"}</td>
                  <td className="px-4 py-3">{shortDate(log.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>
    </div>
  );
}

function RulesView({ isAdmin, rules }: { isAdmin: boolean; rules: RuleDefinition[] }) {
  return (
    <Section
      actions={
        <span
          className={`inline-flex min-h-10 items-center gap-2 rounded-md border px-3 text-sm font-semibold ${isAdmin ? "border-assurance/30 bg-assurance/10 text-assurance" : "border-revenue/30 bg-revenue/10 text-revenue"}`}
        >
          {isAdmin ? (
            <ShieldCheck size={17} aria-hidden="true" />
          ) : (
            <Lock size={17} aria-hidden="true" />
          )}
          {isAdmin ? "Admin access" : "Read only"}
        </span>
      }
      title="Rule Configuration"
    >
      <div className="grid gap-3">
        {rules.map((rule) => (
          <article
            className="rounded-md border border-line bg-white p-4 shadow-panel"
            key={rule.code}
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-sm font-semibold uppercase text-authority">{rule.code}</p>
                <h3 className="mt-1 text-lg font-semibold">{rule.name}</h3>
                <p className="mt-1 text-sm leading-6 text-gray-700">{rule.description}</p>
              </div>
              <div className="flex gap-2">
                <Badge value={rule.severity} />
                <Badge value={rule.active ? "ACTIVE" : "INACTIVE"} />
              </div>
            </div>
            <pre className="mt-3 overflow-auto rounded-md bg-paper p-3 text-xs leading-5">
              {JSON.stringify(rule.thresholdJson, null, 2)}
            </pre>
          </article>
        ))}
      </div>
    </Section>
  );
}

function DataTable<T extends object>({
  columns,
  data,
  filter,
  getRowClass,
  onRowClick,
}: {
  columns: ColumnDef<T>[];
  data: T[];
  filter: string;
  getRowClass?: (row: T) => string;
  onRowClick?: (row: T) => void;
}) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const table = useReactTable({
    columns,
    data,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onGlobalFilterChange: () => undefined,
    onSortingChange: setSorting,
    state: {
      globalFilter: filter,
      sorting,
    },
  });

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] border-collapse text-left text-sm">
        <thead className="bg-paper text-gray-700">
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th className="px-4 py-3 font-semibold" key={header.id}>
                  {header.isPlaceholder ? null : (
                    <button
                      className="inline-flex min-h-9 items-center gap-2 text-left font-semibold"
                      onClick={header.column.getToggleSortingHandler()}
                      type="button"
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      <ArrowDownUp size={14} aria-hidden="true" />
                    </button>
                  )}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.map((row) => (
            <tr
              className={`border-t border-line transition hover:bg-paper ${getRowClass?.(row.original) ?? ""} ${onRowClick ? "cursor-pointer" : ""}`}
              key={row.id}
              onClick={() => onRowClick?.(row.original)}
            >
              {row.getVisibleCells().map((cell) => (
                <td className="px-4 py-3 align-middle" key={cell.id}>
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Section({
  actions,
  children,
  title,
}: {
  actions?: ReactNode;
  children: ReactNode;
  title: string;
}) {
  return (
    <section
      className="min-w-0 rounded-md border border-line bg-white p-4 shadow-panel sm:p-5"
      aria-labelledby={titleId(title)}
    >
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-xl font-semibold" id={titleId(title)}>
          {title}
        </h2>
        {actions}
      </div>
      {children}
    </section>
  );
}

function SearchInput({
  label,
  onChange,
  testId,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  testId?: string;
  value: string;
}) {
  return (
    <label className="relative block w-full max-w-sm">
      <span className="sr-only">{label}</span>
      <Search
        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gray-500"
        size={17}
      />
      <input
        className="min-h-11 w-full rounded-md border border-line bg-white py-2 pl-10 pr-3 text-sm focus:border-authority"
        data-testid={testId}
        onChange={(event) => onChange(event.target.value)}
        placeholder={label}
        value={value}
      />
    </label>
  );
}

function SelectFilter({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: string[];
  value: string;
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-semibold uppercase text-gray-600">{label}</span>
      <select
        className="min-h-11 w-full rounded-md border border-line bg-white px-3 text-sm focus:border-authority"
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        <option value="ALL">All</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option.replace("_", " ")}
          </option>
        ))}
      </select>
    </label>
  );
}

function EmptyState({ label }: { label: string }) {
  return (
    <div className="flex h-full items-center justify-center rounded-md border border-line bg-paper px-4 text-center text-sm font-medium text-gray-700">
      {label}
    </div>
  );
}

function Badge({ value }: { value: string }) {
  return (
    <span
      className={`inline-flex min-h-7 items-center rounded-md border px-2 text-xs font-semibold ${statusTone(value)}`}
    >
      {value.replace("_", " ")}
    </span>
  );
}

function MetricLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-line bg-paper p-3">
      <p className="text-xs font-semibold uppercase text-gray-600">
        {label.replace(/([A-Z])/g, " $1")}
      </p>
      <p className="mt-1 break-words text-sm font-semibold">{value}</p>
    </div>
  );
}

function FlowItem({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
      <div className="flex items-center gap-3">
        <span className="text-authority">{icon}</span>
        <span className="font-medium">{label}</span>
      </div>
      <span className="text-xl font-semibold">{value}</span>
    </div>
  );
}

function InfoList({ items, title }: { items: string[]; title: string }) {
  return (
    <div className="rounded-md border border-line bg-paper p-3">
      <h3 className="text-sm font-semibold">{title}</h3>
      <ul className="mt-2 space-y-2 text-sm text-gray-700">
        {items.length ? items.map((item) => <li key={item}>{item}</li>) : <li>No records</li>}
      </ul>
    </div>
  );
}

function titleId(title: string) {
  return title.toLowerCase().replace(/[^a-z0-9]+/g, "-");
}

function contributionRows(prediction: ModelPrediction | undefined): Array<[string, number]> {
  return Object.entries(prediction?.explanation.featureContributions ?? {})
    .map(([label, value]) => [label, Number(value)] as [string, number])
    .filter(([, value]) => value > 0)
    .sort((left, right) => right[1] - left[1])
    .slice(0, 5);
}

function featureLabel(value: string) {
  return value
    .replace(/([A-Z])/g, " $1")
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
    .trim();
}

function graphTypeLabel(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function uniqueOptions(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort((left, right) =>
    left.localeCompare(right),
  );
}

function scoringErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if ([401, 403].includes(error.status)) {
      return "Sign in with a live API session before running AI scoring.";
    }

    const message = error.message.trim();
    if (message) {
      return `AI scoring failed: ${message}`;
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return `AI scoring failed: ${error.message}`;
  }

  return "AI scoring failed. Check that the backend, analytics service, and taxpayer data are available.";
}

function detailFallback(cases: CaseRecord[], selectedCaseId: string): CaseDetail {
  if (selectedCaseId === demoCaseDetail.detail.id || !cases.length) {
    return demoCaseDetail;
  }

  return {
    detail: cases.find((record) => record.id === selectedCaseId) ?? cases[0],
    events: demoCaseDetail.events,
    evidencePacks: demoCaseDetail.evidencePacks,
  };
}

function profileFallback(ranking: TaxGapRanking[], selectedTaxpayerId: string): TaxpayerProfile {
  const ranked = ranking.find((record) => record.taxpayerId === selectedTaxpayerId);
  if (!ranked) {
    return demoProfile;
  }

  return {
    ...demoProfile,
    kraPin: ranked.taxpayerPin,
    legalName: ranked.taxpayerName,
    taxpayerId: ranked.taxpayerId,
  };
}

function graphFallback(profile: TaxpayerProfile, selectedTaxpayerId: string): TaxpayerGraph {
  const rootId = profile.taxpayerId || selectedTaxpayerId;

  if (!rootId || rootId === demoGraph.taxpayerId) {
    return demoGraph;
  }

  return {
    ...demoGraph,
    highRiskClusters: demoGraph.highRiskClusters.map((cluster) => ({
      ...cluster,
      sourceTaxpayerId: rootId,
      sourceTaxpayerName: profile.legalName,
    })),
    nodes: demoGraph.nodes.map((node) =>
      node.id === demoGraph.taxpayerId ? { ...node, id: rootId, label: profile.legalName } : node,
    ),
    edges: demoGraph.edges.map((edge) => ({
      ...edge,
      sourceId: edge.sourceId === demoGraph.taxpayerId ? rootId : edge.sourceId,
      sourceLabel: edge.sourceId === demoGraph.taxpayerId ? profile.legalName : edge.sourceLabel,
      targetId: edge.targetId === demoGraph.taxpayerId ? rootId : edge.targetId,
      targetLabel: edge.targetId === demoGraph.taxpayerId ? profile.legalName : edge.targetLabel,
    })),
    taxpayerId: rootId,
  };
}
