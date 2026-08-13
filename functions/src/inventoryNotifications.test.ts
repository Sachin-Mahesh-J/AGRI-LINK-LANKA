import { describe, expect, it } from "vitest";
import {
  buildInventoryNotification,
  normalizeStatus,
  shouldNotifyInventoryTransition,
} from "./inventoryNotifications";

describe("inventoryNotifications", () => {
  it("notifies when pending requests are approved or rejected", () => {
    expect(
      shouldNotifyInventoryTransition(
        { status: "Pending" },
        { status: "Approved" },
      ),
    ).toBe(true);
    expect(
      shouldNotifyInventoryTransition(
        { status: "pending" },
        { status: "rejected" },
      ),
    ).toBe(true);
  });

  it("notifies when approved requests are issued", () => {
    expect(
      shouldNotifyInventoryTransition(
        { status: "Approved" },
        { status: "Issued" },
      ),
    ).toBe(true);
  });

  it("ignores unchanged or officer-only edits", () => {
    expect(
      shouldNotifyInventoryTransition(
        { status: "Pending" },
        { status: "Pending" },
      ),
    ).toBe(false);
    expect(
      shouldNotifyInventoryTransition(
        { status: "Approved" },
        { status: "Approved" },
      ),
    ).toBe(false);
  });

  it("builds readable approval messages", () => {
    const message = buildInventoryNotification("Approved", {
      itemName: "Urea fertilizer",
      quantity: 25,
      approvalNote: "Collect from warehouse",
    });

    expect(message.title).toContain("approved");
    expect(message.message).toContain("Urea fertilizer");
    expect(message.message).toContain("Collect from warehouse");
    expect(normalizeStatus("Rejected")).toBe("rejected");
  });
});
