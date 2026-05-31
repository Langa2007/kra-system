import { NextRequest, NextResponse } from "next/server";

const API_BASE_URL = process.env.KRA_API_BASE_URL ?? "http://localhost:8080";

type RouteContext = {
  params: {
    path: string[];
  };
};

async function proxy(request: NextRequest, context: RouteContext) {
  const target = new URL(`/api/${context.params.path.join("/")}`, API_BASE_URL);
  target.search = request.nextUrl.search;

  const headers = new Headers();
  const contentType = request.headers.get("content-type");
  const authorization = request.headers.get("authorization");
  const accept = request.headers.get("accept");

  if (contentType) {
    headers.set("content-type", contentType);
  }
  if (authorization) {
    headers.set("authorization", authorization);
  }
  if (accept) {
    headers.set("accept", accept);
  }

  let response: Response;
  try {
    response = await fetch(target, {
      body: ["GET", "HEAD"].includes(request.method) ? undefined : await request.arrayBuffer(),
      cache: "no-store",
      headers,
      method: request.method,
      redirect: "manual",
    });
  } catch {
    return NextResponse.json(
      { message: "Backend API is unavailable", target: target.origin },
      { status: 503 },
    );
  }

  const responseHeaders = new Headers(response.headers);
  responseHeaders.delete("content-encoding");
  responseHeaders.delete("transfer-encoding");

  return new NextResponse(response.body, {
    headers: responseHeaders,
    status: response.status,
    statusText: response.statusText,
  });
}

export function GET(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function PATCH(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export function PUT(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
