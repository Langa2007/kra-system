import type {
  CaseDetail,
  EvidencePack,
  CaseRecord,
  DataSource,
  IngestionJob,
  LoginResponse,
  RiskSignal,
  RuleDefinition,
  TaxGapRanking,
  TaxGapSummary,
  TaxpayerProfile,
} from "@/lib/types";

const API_PREFIX = "/api/backend";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

type RequestOptions = {
  body?: unknown;
  method?: string;
  token?: string | null;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({
    Accept: "application/json",
  });

  if (options.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    headers,
    method: options.method ?? "GET",
  });

  if (!response.ok) {
    const message = await response.text();
    throw new ApiError(message || response.statusText, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export function login(email: string, password: string) {
  return request<LoginResponse>("/auth/login", {
    body: { email, password },
    method: "POST",
  });
}

export function getMe(token: string) {
  return request<LoginResponse["user"]>("/auth/me", { token });
}

export function getRiskSignals(token: string | null) {
  return request<RiskSignal[]>("/rules/signals?limit=200", { token });
}

export function getTaxGapRanking(token: string | null) {
  return request<TaxGapRanking[]>("/tax-gaps/ranking?limit=25", { token });
}

export function getTaxGapSummary(token: string | null) {
  return request<TaxGapSummary[]>("/tax-gaps/summary", { token });
}

export function getCases(token: string | null) {
  return request<CaseRecord[]>("/cases?limit=200", { token });
}

export function getCaseDetail(token: string | null, caseId: string) {
  return request<CaseDetail>(`/cases/${caseId}`, { token });
}

export function createCase(token: string, riskSignalId: string, priority: string) {
  return request<CaseRecord>("/cases", {
    body: { priority, riskSignalId },
    method: "POST",
    token,
  });
}

export function addCaseNote(token: string, caseId: string, note: string) {
  return request(`/cases/${caseId}/events`, {
    body: { eventType: "NOTE", note },
    method: "POST",
    token,
  });
}

export function generateEvidencePack(token: string, caseId: string) {
  return request<EvidencePack>(`/cases/${caseId}/evidence-packs`, {
    method: "POST",
    token,
  });
}

export async function downloadEvidencePackPdf(token: string, caseId: string, packId: string) {
  const response = await fetch(
    `${API_PREFIX}/cases/${caseId}/evidence-packs/${packId}?format=pdf`,
    {
      headers: {
        Accept: "application/pdf",
        Authorization: `Bearer ${token}`,
      },
    },
  );

  if (!response.ok) {
    const message = await response.text();
    throw new ApiError(message || response.statusText, response.status);
  }

  return response.blob();
}

export function getTaxpayerProfile(token: string | null, taxpayerId: string) {
  return request<TaxpayerProfile>(`/taxpayers/${taxpayerId}/profile`, { token });
}

export function getIngestionJobs(token: string | null) {
  return request<IngestionJob[]>("/ingestion/jobs", { token });
}

export function getDataSources(token: string | null) {
  return request<DataSource[]>("/data-sources", { token });
}

export function getRules(token: string | null) {
  return request<RuleDefinition[]>("/rules", { token });
}
