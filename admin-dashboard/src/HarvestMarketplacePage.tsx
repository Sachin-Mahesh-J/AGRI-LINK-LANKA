import { useMemo, useState, type FormEvent } from "react";
import { Plus, ShoppingBasket, X } from "lucide-react";
import { repository } from "./repository";
import type {
  Buyer,
  BuyerOrganizationType,
  BuyerStatus,
  DocDate,
  HarvestListing,
  HarvestListingStatus,
  HarvestRequest,
  HarvestRequestStatus,
  Recommendation,
} from "./types";
import { useData } from "./useData";

const dateText = (value: DocDate | undefined) => {
  if (!value) return "—";
  const timestamp = value as { toDate?: () => Date };
  const date =
    typeof timestamp.toDate === "function"
      ? timestamp.toDate()
      : new Date(value as string | number | Date);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString();
};

function State({
  loading,
  error,
  empty,
  children,
}: {
  loading: boolean;
  error: string;
  empty?: boolean;
  children: React.ReactNode;
}) {
  if (loading)
    return (
      <div className="state">
        <span className="spinner" /> Loading data…
      </div>
    );
  if (error) return <div className="state error">{error}</div>;
  if (empty) return <div className="state">No records found.</div>;
  return children;
}

function PageHeader({
  eyebrow,
  title,
  action,
}: {
  eyebrow: string;
  title: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="page-header">
      <div>
        <span>{eyebrow}</span>
        <h1>{title}</h1>
      </div>
      {action}
    </header>
  );
}

const LISTING_STATUSES: HarvestListingStatus[] = [
  "draft",
  "listed",
  "reserved",
  "completed",
  "cancelled",
  "hidden",
];

const REQUEST_STATUSES: HarvestRequestStatus[] = [
  "interested",
  "requested",
  "under_review",
  "accepted",
  "rejected",
  "negotiated",
  "reserved",
  "completed",
  "cancelled",
];

const ORG_TYPES: BuyerOrganizationType[] = [
  "wholesaler",
  "supermarket",
  "exporter",
  "food_processor",
  "juice_manufacturer",
  "hotel",
  "restaurant",
  "other",
];

export function HarvestMarketplacePage({
  buyerScopeId,
}: {
  /** When set, limit requests/actions to this buyer profile. */
  buyerScopeId?: string | null;
}) {
  const isBuyerPortal = Boolean(buyerScopeId);
  const [tab, setTab] = useState<
    "buyers" | "listings" | "requests" | "report"
  >(isBuyerPortal ? "listings" : "buyers");
  const buyers = useData(
    isBuyerPortal
      ? async () => {
          if (!buyerScopeId) return [] as Buyer[];
          const buyer = await repository.getBuyer(buyerScopeId);
          return buyer ? [buyer] : [];
        }
      : repository.buyers,
  );
  const listings = useData(() =>
    repository.harvestListings({ buyerVisibleOnly: isBuyerPortal }),
  );
  const requests = useData(() =>
    repository.harvestRequests(
      buyerScopeId ? { buyerId: buyerScopeId } : undefined,
    ),
  );
  const recommendations = useData(
    isBuyerPortal ? async () => [] as Recommendation[] : repository.recommendations,
  );
  const farms = useData(
    isBuyerPortal ? async () => [] : repository.farms,
  );
  const [buyerEditor, setBuyerEditor] = useState<Buyer | null | "new">(null);
  const [linkBuyerId, setLinkBuyerId] = useState<string | null>(null);
  const [requestEditor, setRequestEditor] = useState<HarvestListing | null>(
    null,
  );
  const [filters, setFilters] = useState({
    cropType: "",
    district: "",
    status: "listed",
    minConfidence: "",
  });

  const scopedBuyers = useMemo(() => {
    const list = buyers.data ?? [];
    if (!buyerScopeId) return list;
    return list.filter((item) => item.id === buyerScopeId);
  }, [buyers.data, buyerScopeId]);

  const scopedListings = useMemo(() => {
    let list = listings.data ?? [];
    if (isBuyerPortal) {
      list = list.filter(
        (item) =>
          item.active !== false &&
          item.verified === true &&
          item.visibility !== "hidden" &&
          item.status === "listed",
      );
    }
    if (filters.cropType.trim()) {
      const needle = filters.cropType.trim().toLowerCase();
      list = list.filter((item) =>
        (item.cropType || "").toLowerCase().includes(needle),
      );
    }
    if (filters.district.trim()) {
      const needle = filters.district.trim().toLowerCase();
      list = list.filter(
        (item) =>
          (item.district || "").toLowerCase().includes(needle) ||
          (item.locationText || "").toLowerCase().includes(needle),
      );
    }
    if (filters.status && !isBuyerPortal) {
      list = list.filter((item) => (item.status || "listed") === filters.status);
    }
    if (filters.minConfidence.trim()) {
      const min = Number(filters.minConfidence);
      if (!Number.isNaN(min)) {
        list = list.filter((item) => (item.confidence ?? 0) >= min);
      }
    }
    return list;
  }, [listings.data, buyerScopeId, isBuyerPortal, filters]);

  const scopedRequests = useMemo(() => {
    const list = requests.data ?? [];
    if (!buyerScopeId) return list;
    return list.filter((item) => item.buyerId === buyerScopeId);
  }, [requests.data, buyerScopeId]);

  const harvestRecommendations = useMemo(
    () =>
      (recommendations.data ?? []).filter(
        (item) => (item.type || "").toUpperCase() === "HARVEST",
      ),
    [recommendations.data],
  );

  const pendingBuyers = scopedBuyers.filter((item) => item.status === "pending");
  const activeBuyers = scopedBuyers.filter((item) => item.status === "active");
  const openRequests = scopedRequests.filter(
    (item) =>
      item.status &&
      !["completed", "cancelled", "rejected"].includes(item.status),
  );

  const reviewBuyer = async (
    buyer: Buyer,
    status: Extract<BuyerStatus, "active" | "rejected" | "inactive">,
  ) => {
    try {
      await repository.reviewBuyer(
        buyer.id,
        status,
        status === "active"
          ? "Approved for harvest marketplace"
          : status === "rejected"
            ? "Rejected by administrator"
            : "Deactivated by administrator",
      );
      buyers.reload();
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Review failed.");
    }
  };

  const updateListingStatus = async (
    listing: HarvestListing,
    status: HarvestListingStatus,
  ) => {
    try {
      await repository.updateHarvestListingStatus(listing.id, status, {
        adminNote: `Status set to ${status}`,
      });
      listings.reload();
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Update failed.");
    }
  };

  const updateRequest = async (
    request: HarvestRequest,
    status: HarvestRequestStatus,
  ) => {
    try {
      await repository.updateHarvestRequestStatus(request.id, status, {
        adminNote: isBuyerPortal ? undefined : `Status set to ${status}`,
        buyerNote: isBuyerPortal ? `Updated to ${status}` : undefined,
      });
      requests.reload();
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Update failed.");
    }
  };

  const publishFromRecommendation = async (recommendation: Recommendation) => {
    try {
      const farm = (farms.data ?? []).find(
        (item) =>
          item.path === recommendation.farmPath ||
          (recommendation.farmPath || "").endsWith(`/farms/${item.id}`),
      );
      await repository.publishHarvestListingFromRecommendation(
        recommendation,
        farm,
      );
      listings.reload();
      alert("Harvest listing published from prediction.");
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Publish failed.");
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="HARVEST MARKETPLACE"
        title={
          isBuyerPortal ? "Buyer harvest workspace" : "Harvest demand control"
        }
        action={
          !isBuyerPortal && tab === "buyers" ? (
            <button
              className="primary compact"
              onClick={() => setBuyerEditor("new")}
            >
              <Plus size={17} /> Add buyer
            </button>
          ) : undefined
        }
      />
      <div className="tabs">
        {!isBuyerPortal && (
          <button
            className={tab === "buyers" ? "active" : ""}
            onClick={() => setTab("buyers")}
          >
            Buyers <span>{pendingBuyers.length || scopedBuyers.length}</span>
          </button>
        )}
        <button
          className={tab === "listings" ? "active" : ""}
          onClick={() => setTab("listings")}
        >
          Listings <span>{scopedListings.length}</span>
        </button>
        <button
          className={tab === "requests" ? "active" : ""}
          onClick={() => setTab("requests")}
        >
          Requests <span>{openRequests.length}</span>
        </button>
        <button
          className={tab === "report" ? "active" : ""}
          onClick={() => setTab("report")}
        >
          Report
        </button>
      </div>

      {tab === "buyers" && !isBuyerPortal && (
        <State {...buyers} empty={!scopedBuyers.length}>
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Buyer</th>
                    <th>Type</th>
                    <th>Contact</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {scopedBuyers.map((buyer) => (
                    <tr key={buyer.id}>
                      <td>
                        <strong>{buyer.name ?? "Unnamed"}</strong>
                        <small>{buyer.address || buyer.id}</small>
                      </td>
                      <td>{buyer.organizationType ?? "other"}</td>
                      <td>
                        {buyer.contactName || "—"}
                        <small>
                          {buyer.email || buyer.phone || "No contact"}
                        </small>
                      </td>
                      <td>
                        <span
                          className={`status ${
                            buyer.status === "active" ? "" : "off"
                          }`}
                        >
                          {buyer.status ?? "pending"}
                        </span>
                      </td>
                      <td className="actions">
                        {buyer.status === "pending" && (
                          <>
                            <button
                              className="text-button"
                              onClick={() => reviewBuyer(buyer, "active")}
                            >
                              Approve
                            </button>
                            <button
                              className="text-button danger"
                              onClick={() => reviewBuyer(buyer, "rejected")}
                            >
                              Reject
                            </button>
                          </>
                        )}
                        {buyer.status === "active" && (
                          <>
                            <button
                              className="text-button"
                              onClick={() => reviewBuyer(buyer, "inactive")}
                            >
                              Deactivate
                            </button>
                            {!buyer.uid && (
                              <button
                                className="text-button"
                                onClick={() => setLinkBuyerId(buyer.id)}
                              >
                                Create login
                              </button>
                            )}
                            <button
                              className="text-button"
                              onClick={() => setBuyerEditor(buyer)}
                            >
                              Edit
                            </button>
                          </>
                        )}
                        {buyer.status === "inactive" && (
                          <button
                            className="text-button"
                            onClick={() => reviewBuyer(buyer, "active")}
                          >
                            Activate
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </State>
      )}

      {tab === "listings" && (
        <>
          <section className="card" style={{ marginBottom: 16 }}>
            <div className="form-grid" style={{ display: "grid", gap: 12 }}>
              <label>
                Crop
                <input
                  value={filters.cropType}
                  onChange={(event) =>
                    setFilters((prev) => ({
                      ...prev,
                      cropType: event.target.value,
                    }))
                  }
                  placeholder="e.g. Rice"
                />
              </label>
              <label>
                District / location
                <input
                  value={filters.district}
                  onChange={(event) =>
                    setFilters((prev) => ({
                      ...prev,
                      district: event.target.value,
                    }))
                  }
                  placeholder="e.g. Anuradhapura"
                />
              </label>
              {!isBuyerPortal && (
                <label>
                  Status
                  <select
                    value={filters.status}
                    onChange={(event) =>
                      setFilters((prev) => ({
                        ...prev,
                        status: event.target.value,
                      }))
                    }
                  >
                    <option value="">All</option>
                    {LISTING_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {status}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <label>
                Min confidence
                <input
                  value={filters.minConfidence}
                  onChange={(event) =>
                    setFilters((prev) => ({
                      ...prev,
                      minConfidence: event.target.value,
                    }))
                  }
                  placeholder="e.g. 50"
                />
              </label>
            </div>
          </section>

          {!isBuyerPortal && (
            <State
              loading={recommendations.loading || farms.loading}
              error={recommendations.error || farms.error}
              empty={!harvestRecommendations.length}
            >
              <section className="card table-card" style={{ marginBottom: 16 }}>
                <h3 style={{ margin: "0 0 12px" }}>
                  Publish from HARVEST predictions
                </h3>
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Prediction</th>
                        <th>Farm</th>
                        <th>Confidence</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {harvestRecommendations.slice(0, 12).map((item) => (
                        <tr key={item.id}>
                          <td>
                            <strong>{item.title ?? "Harvest outlook"}</strong>
                            <small>{item.message?.slice(0, 120) || "—"}</small>
                          </td>
                          <td>
                            {item.farmPath?.split("/").at(-1) ?? "—"}
                            <small>{item.activityStatus || item.source}</small>
                          </td>
                          <td>{item.confidence ?? "—"}%</td>
                          <td className="actions">
                            <button
                              className="text-button"
                              onClick={() => publishFromRecommendation(item)}
                            >
                              Publish listing
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </State>
          )}

          <State
            loading={listings.loading}
            error={listings.error}
            empty={!scopedListings.length}
          >
            <section className="card table-card">
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Listing</th>
                      <th>Location</th>
                      <th>Quantity</th>
                      <th>Confidence</th>
                      <th>Status</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {scopedListings.map((listing) => (
                      <tr key={listing.id}>
                        <td>
                          <strong>{listing.cropType || "Crop"}</strong>
                          <small>
                            {listing.farmName || listing.farmId || "—"} ·{" "}
                            {listing.listingOrigin || "prediction"}
                          </small>
                        </td>
                        <td>
                          {listing.district || listing.locationText || "—"}
                          <small>{listing.harvestPeriodLabel || "—"}</small>
                        </td>
                        <td>
                          {listing.estimatedQuantityMax ??
                            listing.estimatedQuantityMin ??
                            "—"}{" "}
                          {listing.quantityUnit || "tonnes"}
                        </td>
                        <td>
                          {listing.confidence ?? "—"}%
                          <small>{listing.reliabilityLabel || ""}</small>
                        </td>
                        <td>
                          <span className="status">
                            {listing.status ?? "listed"}
                          </span>
                        </td>
                        <td className="actions">
                          {isBuyerPortal ? (
                            <button
                              className="text-button"
                              onClick={() => setRequestEditor(listing)}
                            >
                              Request / interest
                            </button>
                          ) : (
                            <>
                              {!listing.verified && (
                                <button
                                  className="text-button"
                                  onClick={async () => {
                                    await repository.saveHarvestListing(
                                      listing.id,
                                      {
                                        ...listing,
                                        verified: true,
                                        active: true,
                                        status: listing.status || "listed",
                                        visibility:
                                          listing.visibility || "public",
                                      },
                                    );
                                    listings.reload();
                                  }}
                                >
                                  Verify
                                </button>
                              )}
                              <select
                                value={listing.status ?? "listed"}
                                onChange={(event) =>
                                  updateListingStatus(
                                    listing,
                                    event.target.value as HarvestListingStatus,
                                  )
                                }
                              >
                                {LISTING_STATUSES.map((status) => (
                                  <option key={status} value={status}>
                                    {status}
                                  </option>
                                ))}
                              </select>
                            </>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </State>
        </>
      )}

      {tab === "requests" && (
        <State {...requests} empty={!scopedRequests.length}>
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Request</th>
                    <th>Crop / farm</th>
                    <th>Quantity</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {scopedRequests.map((request) => (
                    <tr key={request.id}>
                      <td>
                        <strong>{request.buyerName || request.buyerId}</strong>
                        <small>{dateText(request.createdAt)}</small>
                      </td>
                      <td>
                        {request.cropType || "—"}
                        <small>
                          {request.farmName || request.farmId || "—"}
                        </small>
                      </td>
                      <td>
                        {request.requestedQuantity ?? "—"}{" "}
                        {request.quantityUnit || ""}
                      </td>
                      <td>
                        <span className="status">
                          {request.status ?? "requested"}
                        </span>
                      </td>
                      <td className="actions">
                        <select
                          value={request.status ?? "requested"}
                          onChange={(event) =>
                            updateRequest(
                              request,
                              event.target.value as HarvestRequestStatus,
                            )
                          }
                        >
                          {REQUEST_STATUSES.map((status) => (
                            <option key={status} value={status}>
                              {status}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </State>
      )}

      {tab === "report" && (
        <div className="kpi-grid">
          <section className="card kpi">
            <ShoppingBasket size={20} />
            <div>
              <span>Active buyers</span>
              <strong>{activeBuyers.length}</strong>
            </div>
          </section>
          <section className="card kpi">
            <div>
              <span>Listed harvests</span>
              <strong>
                {
                  (listings.data ?? []).filter(
                    (item) => item.status === "listed" && item.active !== false,
                  ).length
                }
              </strong>
            </div>
          </section>
          <section className="card kpi">
            <div>
              <span>Open buyer requests</span>
              <strong>{openRequests.length}</strong>
            </div>
          </section>
          <section className="card kpi">
            <div>
              <span>From predictions</span>
              <strong>
                {
                  (listings.data ?? []).filter(
                    (item) => item.listingOrigin === "prediction",
                  ).length
                }
              </strong>
            </div>
          </section>
        </div>
      )}

      {buyerEditor && (
        <BuyerModal
          buyer={buyerEditor === "new" ? undefined : buyerEditor}
          close={() => setBuyerEditor(null)}
          saved={() => {
            setBuyerEditor(null);
            buyers.reload();
          }}
        />
      )}

      {linkBuyerId && (
        <BuyerLoginModal
          buyerId={linkBuyerId}
          buyer={scopedBuyers.find((item) => item.id === linkBuyerId)}
          close={() => setLinkBuyerId(null)}
          saved={() => {
            setLinkBuyerId(null);
            buyers.reload();
          }}
        />
      )}

      {requestEditor && buyerScopeId && (
        <HarvestRequestModal
          listing={requestEditor}
          buyer={scopedBuyers[0]}
          buyerId={buyerScopeId}
          close={() => setRequestEditor(null)}
          saved={() => {
            setRequestEditor(null);
            requests.reload();
          }}
        />
      )}
    </>
  );
}

function BuyerModal({
  buyer,
  close,
  saved,
}: {
  buyer?: Buyer;
  close: () => void;
  saved: () => void;
}) {
  const [name, setName] = useState(buyer?.name ?? "");
  const [contactName, setContactName] = useState(buyer?.contactName ?? "");
  const [email, setEmail] = useState(buyer?.email ?? "");
  const [phone, setPhone] = useState(buyer?.phone ?? "");
  const [address, setAddress] = useState(buyer?.address ?? "");
  const [organizationType, setOrganizationType] = useState(
    buyer?.organizationType ?? "wholesaler",
  );
  const [status, setStatus] = useState<BuyerStatus>(buyer?.status ?? "pending");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await repository.saveBuyer(buyer?.id, {
      name,
      contactName,
      email,
      phone,
      address,
      organizationType,
      status,
      uid: buyer?.uid ?? null,
      approvalNote: buyer?.approvalNote || "",
    });
    saved();
  };

  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">BUYER PROFILE</span>
        <h2>{buyer?.id ? "Edit buyer" : "Register buyer"}</h2>
        <label>
          Organisation name
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
          />
        </label>
        <label>
          Organisation type
          <select
            value={organizationType}
            onChange={(event) => setOrganizationType(event.target.value)}
          >
            {ORG_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>
        <label>
          Contact name
          <input
            value={contactName}
            onChange={(event) => setContactName(event.target.value)}
          />
        </label>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          Phone
          <input
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
          />
        </label>
        <label>
          Address
          <input
            value={address}
            onChange={(event) => setAddress(event.target.value)}
          />
        </label>
        <label>
          Status
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as BuyerStatus)}
          >
            <option value="pending">pending</option>
            <option value="active">active</option>
            <option value="inactive">inactive</option>
            <option value="rejected">rejected</option>
          </select>
        </label>
        <div className="modal-actions">
          <button type="button" className="ghost" onClick={close}>
            Cancel
          </button>
          <button className="primary" type="submit">
            Save buyer
          </button>
        </div>
      </form>
    </div>
  );
}

function BuyerLoginModal({
  buyerId,
  buyer,
  close,
  saved,
}: {
  buyerId: string;
  buyer?: Buyer;
  close: () => void;
  saved: () => void;
}) {
  const [email, setEmail] = useState(buyer?.email ?? "");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState(buyer?.name ?? "");
  const [phone, setPhone] = useState(buyer?.phone ?? "");
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    try {
      await repository.createBuyerAccount(
        email,
        password,
        buyerId,
        displayName || buyer?.name || "Buyer",
        phone,
      );
      saved();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Create failed.");
    }
  };

  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">BUYER LOGIN</span>
        <h2>Create buyer account</h2>
        <label>
          Display name
          <input
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            required
          />
        </label>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label>
          Temporary password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            minLength={6}
          />
        </label>
        <label>
          Phone
          <input
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
          />
        </label>
        {error && <div className="state error">{error}</div>}
        <div className="modal-actions">
          <button type="button" className="ghost" onClick={close}>
            Cancel
          </button>
          <button className="primary" type="submit">
            Create login
          </button>
        </div>
      </form>
    </div>
  );
}

function HarvestRequestModal({
  listing,
  buyer,
  buyerId,
  close,
  saved,
}: {
  listing: HarvestListing;
  buyer?: Buyer;
  buyerId: string;
  close: () => void;
  saved: () => void;
}) {
  const [quantity, setQuantity] = useState(
    listing.estimatedQuantityMax?.toString() || "",
  );
  const [message, setMessage] = useState("");
  const [asInterest, setAsInterest] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await repository.createHarvestRequest({
      harvestListingId: listing.id,
      buyerId,
      buyerUid: buyer?.uid || "",
      buyerName: buyer?.name || "",
      farmId: listing.farmId || "",
      farmPath: listing.farmPath || "",
      farmName: listing.farmName || "",
      cropType: listing.cropType || "",
      requestedQuantity: quantity,
      quantityUnit: listing.quantityUnit || "tonnes",
      message,
      status: asInterest ? "interested" : "requested",
      buyerNote: message,
    });
    saved();
  };

  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">HARVEST REQUEST</span>
        <h2>
          {listing.cropType || "Harvest"} · {listing.farmName || listing.farmId}
        </h2>
        <p style={{ marginTop: 0, color: "var(--muted, #667)" }}>
          Non-binding interest or purchase request. Final sale is confirmed later
          by AgriLink staff.
        </p>
        <label>
          Requested quantity ({listing.quantityUnit || "tonnes"})
          <input
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            required
          />
        </label>
        <label>
          Message
          <textarea
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            rows={3}
          />
        </label>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={asInterest}
            onChange={(event) => setAsInterest(event.target.checked)}
          />
          Express interest only (not a formal purchase request)
        </label>
        <div className="modal-actions">
          <button type="button" className="ghost" onClick={close}>
            Cancel
          </button>
          <button className="primary" type="submit">
            Submit
          </button>
        </div>
      </form>
    </div>
  );
}
