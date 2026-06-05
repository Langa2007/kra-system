import { expect, test } from "@playwright/test";

test("loads the operational dashboard", async ({ page }) => {
  await page.goto("/");

  await expect(
    page.getByRole("heading", { name: "Officer and Executive Dashboard" }),
  ).toBeVisible();
  await expect(page.getByRole("heading", { name: "Operational Snapshot" })).toBeVisible();
  await expect(page.getByText("Estimated exposure")).toBeVisible();
  await expect(page.getByText("Demo data")).toBeVisible();
});

test("logs in and applies officer role access", async ({ page }) => {
  await page.route("**/api/backend/auth/login", async (route) => {
    await route.fulfill({
      body: JSON.stringify({
        accessToken: "test-token",
        tokenType: "Bearer",
        user: {
          email: "officer@example.test",
          fullName: "Officer User",
          id: "officer-1",
          roles: ["OFFICER"],
        },
      }),
      contentType: "application/json",
      status: 200,
    });
  });

  await page.goto("/");
  await page.getByLabel("Email").fill("officer@example.test");
  await page.getByLabel("Password").fill("secret");
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page.getByText("Officer User", { exact: true })).toBeVisible();
  await page.getByTestId("nav-rules").click();
  await expect(page.getByText("Read only")).toBeVisible();
});

test("filters the risk queue and opens a demo case", async ({ page }) => {
  await page.goto("/");

  await page.getByTestId("nav-risks").click();
  await expect(page.getByRole("heading", { name: "Priority Risk Queue" })).toBeVisible();
  await page.getByRole("button", { name: /Gap/ }).click();
  await expect(
    page.getByRole("row").nth(1).getByRole("button", { name: "Kisumu County Channel 4" }),
  ).toBeVisible();
  await page.getByTestId("risk-filter").fill("Amani");

  await expect(page.getByRole("button", { name: "Amani Wholesale Traders" })).toBeVisible();
  await page.getByRole("button", { name: "Open case" }).first().click();

  await expect(page.getByText("Demo case preview selected")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Case Detail" })).toBeVisible();
  await expect(page.getByLabel("Case Detail").getByText("CASE-20260531-001")).toBeVisible();
});

test("shows taxpayer profile from the taxpayer search", async ({ page }) => {
  await page.goto("/");

  await page.getByTestId("nav-taxpayers").click();
  await page.getByTestId("taxpayer-search").fill("Rift");
  await page.getByRole("button", { name: /Rift Valley Logistics Ltd/ }).click();

  await expect(page.getByRole("heading", { name: "Taxpayer Profile" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Rift Valley Logistics Ltd" })).toBeVisible();
});

test("supports case notes and evidence preview", async ({ page }) => {
  await page.goto("/");

  await page.getByTestId("nav-cases").click();
  await page.getByLabel("Case note").fill("Follow up on source records.");
  await page.getByTestId("add-case-note").click();

  await expect(page.getByText("Demo note previewed")).toBeVisible();

  await page.getByRole("button", { name: "Generate evidence" }).click();
  await expect(page.getByRole("heading", { name: "Evidence Pack Viewer" })).toBeVisible();
  await expect(page.getByText("VAT_OUTPUT_MISMATCH")).toBeVisible();
});

test("renders ingestion and role-aware rule views", async ({ page }) => {
  await page.goto("/");

  await page.getByTestId("nav-ingestion").click();
  await expect(page.getByRole("heading", { name: "Ingestion Status" })).toBeVisible();
  await expect(page.getByText("etims-may-demo.csv")).toBeVisible();

  await page.getByTestId("nav-rules").click();
  await expect(page.getByRole("heading", { name: "Rule Configuration" })).toBeVisible();
  await expect(page.getByText("Admin access")).toBeVisible();
  await expect(page.getByText("VAT_OUTPUT_MISMATCH")).toBeVisible();
});

test("shows settlement reconciliation scope", async ({ page }) => {
  await page.goto("/");

  await page.getByTestId("nav-settlements").click();
  await expect(page.getByRole("heading", { name: "Settlement Monitor" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Run reconciliation" })).toBeVisible();
  await expect(page.getByText("Missing settlements")).toBeVisible();
  await expect(page.getByText("Exception Report")).toBeVisible();
});

test("supports voluntary compliance nudges in demo mode", async ({ page }) => {
  await page.goto("/");

  await page.getByTestId("nav-notifications").click();
  await expect(page.getByRole("heading", { name: "Generate Nudge" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Communication History" })).toBeVisible();
  await expect(page.getByText("SOFT_COMPLIANCE_EMAIL")).toBeVisible();

  await page.locator("#nudge-case").selectOption("case-002");
  await page.getByRole("button", { name: "EMAIL" }).first().click();
  await expect(page.getByText("Demo EMAIL case nudge previewed")).toBeVisible();

  await page.getByLabel("Response note").fill("Return amended and payment plan requested.");
  await page.getByRole("button", { name: "Record response" }).click();
  await expect(page.getByText("Demo taxpayer response previewed")).toBeVisible();
});

test("shows the phase 16 pilot readiness package", async ({ page }) => {
  await page.goto("/");

  await page.getByTestId("nav-pilot").click();
  await expect(page.getByRole("heading", { name: "Pilot Readiness" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "ROI Calculator" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Pilot Documents" })).toBeVisible();
  await expect(page.getByText("docs/phase16/pilot-proposal.md")).toBeVisible();
  await expect(page.getByRole("button", { name: "Pilot PDF" })).toBeVisible();
  await expect(page.getByRole("button", { name: "ROI Workbook" })).toBeVisible();
});

test("handles a large synthetic risk queue with filtering", async ({ page }) => {
  const syntheticSignals = Array.from({ length: 200 }, (_, index) => ({
    confidenceScore: 80 + (index % 15),
    createdAt: "2026-05-31T08:00:00Z",
    declaredAmount: 1_000_000 + index,
    estimatedGap: 2_000_000 + index * 10_000,
    evidence: { sourceRecords: [`INV-${index}`] },
    explanation: "Synthetic load row",
    id: `signal-${index}`,
    observedAmount: 3_000_000 + index,
    periodEnd: "2026-05-31",
    periodStart: "2026-05-01",
    ruleCode: "VAT_OUTPUT_MISMATCH",
    severity: index % 2 === 0 ? "HIGH" : "MEDIUM",
    signalType: "DETERMINISTIC",
    status: "OPEN",
    taxHead: "VAT",
    taxpayerId: `taxpayer-${index}`,
    taxpayerName: `Synthetic Trader ${index}`,
    taxpayerPin: `P${String(index).padStart(9, "0")}Z`,
  }));

  await page.route("**/api/backend/**", async (route) => {
    const url = route.request().url();
    const body = (() => {
      if (url.includes("/auth/login")) {
        return {
          accessToken: "load-test-token",
          tokenType: "Bearer",
          user: {
            email: "admin@example.test",
            fullName: "Admin User",
            id: "admin-1",
            roles: ["ADMIN"],
          },
        };
      }
      if (url.includes("/rules/signals")) {
        return syntheticSignals;
      }
      if (url.includes("/tax-gaps/summary")) {
        return [];
      }
      if (url.includes("/reconciliation/summary")) {
        return {
          delayedCount: 0,
          duplicateCount: 0,
          exceptionCount: 0,
          expectedAmount: 0,
          missingCount: 0,
          resultCount: 0,
          settledAmount: 0,
          varianceAmount: 0,
          wrongAccountCount: 0,
        };
      }
      if (url.includes("/notifications/templates")) {
        return [];
      }
      return [];
    })();

    await route.fulfill({
      body: JSON.stringify(body),
      contentType: "application/json",
      status: 200,
    });
  });

  await page.goto("/");
  await page.getByLabel("Email").fill("admin@example.test");
  await page.getByLabel("Password").fill("secret");
  await page.getByRole("button", { name: "Sign in" }).click();
  await page.getByTestId("nav-risks").click();
  await page.getByTestId("risk-filter").fill("Synthetic Trader 199");

  await expect(page.getByRole("button", { name: "Synthetic Trader 199" })).toBeVisible();
  await expect(page.getByRole("button", { exact: true, name: "Synthetic Trader 1" })).toHaveCount(
    0,
  );
});
