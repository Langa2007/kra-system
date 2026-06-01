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

  await page.route("**/api/backend/auth/login", async (route) => {
    await route.fulfill({
      body: JSON.stringify({
        accessToken: "load-test-token",
        tokenType: "Bearer",
        user: {
          email: "admin@example.test",
          fullName: "Admin User",
          id: "admin-1",
          roles: ["ADMIN"],
        },
      }),
      contentType: "application/json",
      status: 200,
    });
  });
  await page.route("**/api/backend/rules/signals**", async (route) => {
    await route.fulfill({
      body: JSON.stringify(syntheticSignals),
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
