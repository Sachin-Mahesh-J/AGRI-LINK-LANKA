import { describe, expect, it } from "vitest";
import {
  classifyReading,
  extractBearerToken,
  hashIngestKey,
  parseRecordedAt,
  safeEqualString,
} from "./iotIngest";

describe("iotIngest helpers", () => {
  it("classifies critical moisture and water levels", () => {
    expect(classifyReading(12, 30, 60, 8)).toBe("Critical");
    expect(classifyReading(20, 36, 40, 20)).toBe("Warning");
    expect(classifyReading(50, 28, 60, 55)).toBe("Normal");
  });

  it("extracts bearer tokens", () => {
    expect(extractBearerToken("Bearer secret-key")).toBe("secret-key");
    expect(extractBearerToken("bearer abc")).toBe("abc");
    expect(extractBearerToken("Token abc")).toBeNull();
  });

  it("compares keys safely", () => {
    expect(safeEqualString("same", "same")).toBe(true);
    expect(safeEqualString("same", "diff")).toBe(false);
    expect(safeEqualString("short", "longer")).toBe(false);
  });

  it("hashes ingest keys deterministically", () => {
    expect(hashIngestKey("abc")).toBe(hashIngestKey("abc"));
    expect(hashIngestKey("abc")).not.toBe(hashIngestKey("abcd"));
  });

  it("parses recordedAt as millis", () => {
    const now = Date.parse("2025-06-01T00:00:00.000Z");
    expect(parseRecordedAt(1_700_000_000_000, now)).toBe(1_700_000_000_000);
    expect(parseRecordedAt(1_700_000_000, now)).toBe(1_700_000_000_000);
    expect(parseRecordedAt("2024-01-01T00:00:00.000Z", now)).toBe(
      Date.parse("2024-01-01T00:00:00.000Z"),
    );
  });

  it("rejects ESP uptime millis as recordedAt", () => {
    const now = Date.parse("2025-06-01T00:00:00.000Z");
    // Typical millis() uptime after a few minutes
    expect(parseRecordedAt(180_000, now)).toBe(now);
    expect(parseRecordedAt(undefined, now)).toBe(now);
  });
});
