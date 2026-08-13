import { describe, expect, it } from "vitest";
import {
  buildHarvestRequestBuyerNotification,
  buildHarvestRequestOfficerNotification,
  buildProductRequestNotification,
  officerUidFromFarmPath,
  shouldNotifyBuyerOfHarvestUpdate,
  shouldNotifyStatusChange,
} from "./marketplaceNotifications";

describe("marketplaceNotifications", () => {
  it("extracts officer uid from farmPath", () => {
    expect(officerUidFromFarmPath("users/abc123/farms/farm-1")).toBe("abc123");
    expect(officerUidFromFarmPath("users/abc123/farms/")).toBeNull();
    expect(officerUidFromFarmPath("")).toBeNull();
  });

  it("notifies only when status actually changes", () => {
    expect(shouldNotifyStatusChange("created", "accepted")).toBe(true);
    expect(shouldNotifyStatusChange("Accepted", "accepted")).toBe(false);
    expect(shouldNotifyStatusChange("requested", "requested")).toBe(false);
  });

  it("builds supplier request messages", () => {
    const message = buildProductRequestNotification("Accepted", {
      productName: "Urea 46%",
      supplierName: "GreenAgro",
      quantity: "10",
      unit: "bags",
      supplierNote: "Ready Friday",
    });
    expect(message.title).toContain("accepted");
    expect(message.message).toContain("Urea 46%");
    expect(message.message).toContain("GreenAgro");
    expect(message.message).toContain("Ready Friday");
  });

  it("builds harvest create and buyer response messages", () => {
    const created = buildHarvestRequestOfficerNotification(
      "requested",
      {
        buyerName: "FreshMart",
        cropType: "Rice",
        farmName: "North Plot",
        requestedQuantity: "2",
        quantityUnit: "tonnes",
      },
      true,
    );
    expect(created.title).toContain("buyer interest");
    expect(created.message).toContain("FreshMart");

    const buyer = buildHarvestRequestBuyerNotification("accepted", {
      cropType: "Rice",
      farmName: "North Plot",
      requestedQuantity: "2",
      quantityUnit: "tonnes",
      officerNote: "Confirm pickup week",
    });
    expect(buyer.title).toContain("accepted");
    expect(buyer.message).toContain("Confirm pickup week");
  });

  it("notifies buyers for response statuses", () => {
    expect(
      shouldNotifyBuyerOfHarvestUpdate(
        { status: "requested" },
        { status: "accepted" },
      ),
    ).toBe(true);
    expect(
      shouldNotifyBuyerOfHarvestUpdate(
        { status: "requested" },
        { status: "requested" },
      ),
    ).toBe(false);
  });
});
