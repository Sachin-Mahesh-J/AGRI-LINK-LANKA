import { useMemo, useState, type FormEvent } from "react";
import { Plus, Store, Trash2, X } from "lucide-react";
import { repository } from "./repository";
import type {
  DocDate,
  ProductRequest,
  ProductRequestStatus,
  Supplier,
  SupplierProduct,
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
  if (error)
    return <div className="state error">{error}</div>;
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

const REQUEST_STATUSES: ProductRequestStatus[] = [
  "created",
  "reviewed",
  "accepted",
  "rejected",
  "preparing",
  "dispatched",
  "delivered",
  "cancelled",
];

export function MarketplacePage({
  supplierScopeId,
}: {
  /** When set, limit products/requests to this supplier profile. */
  supplierScopeId?: string | null;
}) {
  const isSupplierPortal = Boolean(supplierScopeId);
  const [tab, setTab] = useState<
    "approvals" | "products" | "requests" | "report"
  >(isSupplierPortal ? "products" : "approvals");
  const suppliers = useData(repository.suppliers);
  const products = useData(repository.supplierProducts);
  const requests = useData(repository.productRequests);
  const [productEditor, setProductEditor] = useState<SupplierProduct | null | "new">(
    null,
  );
  const [linkSupplierId, setLinkSupplierId] = useState<string | null>(null);

  const scopedSuppliers = useMemo(() => {
    const list = suppliers.data ?? [];
    if (!supplierScopeId) return list;
    return list.filter((item) => item.id === supplierScopeId);
  }, [suppliers.data, supplierScopeId]);

  const scopedProducts = useMemo(() => {
    const list = products.data ?? [];
    if (!supplierScopeId) return list;
    return list.filter((item) => item.supplierId === supplierScopeId);
  }, [products.data, supplierScopeId]);

  const scopedRequests = useMemo(() => {
    const list = requests.data ?? [];
    if (!supplierScopeId) return list;
    return list.filter((item) => item.supplierId === supplierScopeId);
  }, [requests.data, supplierScopeId]);

  const pendingSuppliers = scopedSuppliers.filter(
    (item) => item.status === "pending",
  );
  const activeSuppliers = scopedSuppliers.filter(
    (item) => item.status === "active",
  );
  const openRequests = scopedRequests.filter(
    (item) =>
      item.status &&
      !["delivered", "cancelled", "rejected"].includes(item.status),
  );

  const reviewSupplier = async (
    supplier: Supplier,
    status: "active" | "rejected" | "inactive",
  ) => {
    try {
      await repository.reviewSupplier(
        supplier.id,
        status,
        status === "active"
          ? "Approved for marketplace participation"
          : status === "rejected"
            ? "Rejected by administrator"
            : "Deactivated by administrator",
      );
      suppliers.reload();
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Review failed.");
    }
  };

  const updateRequest = async (
    request: ProductRequest,
    status: ProductRequestStatus,
  ) => {
    try {
      await repository.updateProductRequestStatus(request.id, status, {
        adminNote: isSupplierPortal ? undefined : `Status set to ${status}`,
        supplierNote: isSupplierPortal ? `Updated to ${status}` : undefined,
      });
      requests.reload();
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Update failed.");
    }
  };

  const removeProduct = async (id: string) => {
    if (!confirm("Delete this supplier product?")) return;
    await repository.removeSupplierProduct(id);
    products.reload();
  };

  return (
    <>
      <PageHeader
        eyebrow="SUPPLIER MARKETPLACE"
        title={isSupplierPortal ? "My supplier workspace" : "Marketplace control"}
        action={
          tab === "products" ? (
            <button
              className="primary compact"
              onClick={() => setProductEditor("new")}
            >
              <Plus size={17} /> Add product
            </button>
          ) : undefined
        }
      />
      <div className="tabs">
        {!isSupplierPortal && (
          <button
            className={tab === "approvals" ? "active" : ""}
            onClick={() => setTab("approvals")}
          >
            Approvals <span>{pendingSuppliers.length}</span>
          </button>
        )}
        <button
          className={tab === "products" ? "active" : ""}
          onClick={() => setTab("products")}
        >
          Products <span>{scopedProducts.length}</span>
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

      {tab === "approvals" && !isSupplierPortal && (
        <State {...suppliers} empty={!scopedSuppliers.length}>
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Supplier</th>
                    <th>Contact</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {scopedSuppliers.map((supplier) => (
                    <tr key={supplier.id}>
                      <td>
                        <strong>{supplier.name ?? "Unnamed"}</strong>
                        <small>{supplier.address || supplier.id}</small>
                      </td>
                      <td>
                        {supplier.contactName || "—"}
                        <small>
                          {supplier.email || supplier.phone || "No contact"}
                        </small>
                      </td>
                      <td>
                        <span
                          className={`status ${
                            supplier.status === "active" ? "" : "off"
                          }`}
                        >
                          {supplier.status ?? "pending"}
                        </span>
                      </td>
                      <td className="actions">
                        {supplier.status === "pending" && (
                          <>
                            <button
                              className="text-button"
                              onClick={() => reviewSupplier(supplier, "active")}
                            >
                              Approve
                            </button>
                            <button
                              className="text-button danger"
                              onClick={() =>
                                reviewSupplier(supplier, "rejected")
                              }
                            >
                              Reject
                            </button>
                          </>
                        )}
                        {supplier.status === "active" && (
                          <>
                            <button
                              className="text-button"
                              onClick={() =>
                                reviewSupplier(supplier, "inactive")
                              }
                            >
                              Deactivate
                            </button>
                            {!supplier.uid && (
                              <button
                                className="text-button"
                                onClick={() => setLinkSupplierId(supplier.id)}
                              >
                                Create login
                              </button>
                            )}
                          </>
                        )}
                        {supplier.status === "inactive" && (
                          <button
                            className="text-button"
                            onClick={() => reviewSupplier(supplier, "active")}
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

      {tab === "products" && (
        <State
          loading={products.loading || suppliers.loading}
          error={products.error || suppliers.error}
          empty={!scopedProducts.length}
        >
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Category</th>
                    <th>Supplier</th>
                    <th>Availability</th>
                    <th>Verified</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {scopedProducts.map((product) => (
                    <tr key={product.id}>
                      <td>
                        <strong>{product.name ?? "Unnamed"}</strong>
                        <small>
                          {product.packSize || product.unit || "—"}
                          {product.price != null ? ` · ${product.price}` : ""}
                        </small>
                      </td>
                      <td>{product.category ?? "—"}</td>
                      <td>{product.supplierName ?? product.supplierId}</td>
                      <td>
                        <span className="badge">
                          {product.availabilityStatus ?? "available"}
                        </span>
                        {!product.active && (
                          <span className="status off"> inactive</span>
                        )}
                      </td>
                      <td>{product.verified ? "Yes" : "Pending"}</td>
                      <td className="actions">
                        <button
                          className="text-button"
                          onClick={() => setProductEditor(product)}
                        >
                          Edit
                        </button>
                        {!isSupplierPortal && !product.verified && (
                          <button
                            className="text-button"
                            onClick={async () => {
                              await repository.saveSupplierProduct(product.id, {
                                ...product,
                                verified: true,
                                active: true,
                              });
                              products.reload();
                            }}
                          >
                            Verify
                          </button>
                        )}
                        <button
                          className="icon-button"
                          onClick={() => removeProduct(product.id)}
                        >
                          <Trash2 size={16} />
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

      {tab === "requests" && (
        <State {...requests} empty={!scopedRequests.length}>
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Request</th>
                    <th>Category</th>
                    <th>Quantity</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {scopedRequests.map((request) => (
                    <tr key={request.id}>
                      <td>
                        <strong>
                          {request.productName ?? "Supplier product"}
                        </strong>
                        <small>
                          {request.supplierName ?? request.supplierId} ·{" "}
                          {dateText(request.createdAt)}
                        </small>
                      </td>
                      <td>{request.productCategory ?? "—"}</td>
                      <td>
                        {request.quantity ?? 0} {request.unit ?? ""}
                      </td>
                      <td>
                        <span className="status">{request.status ?? "created"}</span>
                      </td>
                      <td className="actions">
                        <select
                          value={request.status ?? "created"}
                          onChange={(event) =>
                            updateRequest(
                              request,
                              event.target.value as ProductRequestStatus,
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
            <Store size={20} />
            <div>
              <span>Active suppliers</span>
              <strong>{activeSuppliers.length}</strong>
            </div>
          </section>
          <section className="card kpi">
            <div>
              <span>Verified products</span>
              <strong>
                {scopedProducts.filter((item) => item.verified).length}
              </strong>
            </div>
          </section>
          <section className="card kpi">
            <div>
              <span>Open product requests</span>
              <strong>{openRequests.length}</strong>
            </div>
          </section>
          <section className="card kpi">
            <div>
              <span>Delivered</span>
              <strong>
                {
                  scopedRequests.filter((item) => item.status === "delivered")
                    .length
                }
              </strong>
            </div>
          </section>
        </div>
      )}

      {productEditor && (
        <ProductModal
          product={productEditor === "new" ? undefined : productEditor}
          suppliers={
            isSupplierPortal
              ? scopedSuppliers
              : scopedSuppliers.filter((item) => item.status === "active")
          }
          defaultSupplierId={supplierScopeId ?? undefined}
          close={() => setProductEditor(null)}
          saved={() => {
            setProductEditor(null);
            products.reload();
          }}
          adminMode={!isSupplierPortal}
        />
      )}

      {linkSupplierId && (
        <SupplierLoginModal
          supplierId={linkSupplierId}
          supplier={scopedSuppliers.find((item) => item.id === linkSupplierId)}
          close={() => setLinkSupplierId(null)}
          saved={() => {
            setLinkSupplierId(null);
            suppliers.reload();
          }}
        />
      )}
    </>
  );
}

function ProductModal({
  product,
  suppliers,
  defaultSupplierId,
  close,
  saved,
  adminMode,
}: {
  product?: SupplierProduct;
  suppliers: Supplier[];
  defaultSupplierId?: string;
  close: () => void;
  saved: () => void;
  adminMode: boolean;
}) {
  const [supplierId, setSupplierId] = useState(
    product?.supplierId ?? defaultSupplierId ?? suppliers[0]?.id ?? "",
  );
  const [name, setName] = useState(product?.name ?? "");
  const [category, setCategory] = useState(product?.category ?? "Fertilizers");
  const [description, setDescription] = useState(product?.description ?? "");
  const [unit, setUnit] = useState(product?.unit ?? "units");
  const [packSize, setPackSize] = useState(product?.packSize ?? "");
  const [price, setPrice] = useState(product?.price?.toString() ?? "");
  const [availabilityStatus, setAvailabilityStatus] = useState<
    "available" | "limited" | "out_of_stock"
  >(product?.availabilityStatus ?? "available");
  const [cropSuitability, setCropSuitability] = useState(
    (product?.cropSuitability ?? []).join(", "),
  );
  const [active, setActive] = useState(product?.active !== false);
  const [verified, setVerified] = useState(product?.verified === true);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const supplier = suppliers.find((item) => item.id === supplierId);
    await repository.saveSupplierProduct(product?.id, {
      supplierId,
      supplierName: supplier?.name ?? product?.supplierName ?? "",
      name,
      category,
      description,
      unit,
      packSize,
      price,
      availabilityStatus,
      cropSuitability: cropSuitability
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
      active,
      verified: adminMode ? verified : product?.verified === true,
    });
    saved();
  };

  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">SUPPLIER PRODUCT</span>
        <h2>{product?.id ? "Edit product" : "List product"}</h2>
        <label>
          Supplier
          <select
            value={supplierId}
            onChange={(event) => setSupplierId(event.target.value)}
            required
            disabled={Boolean(defaultSupplierId)}
          >
            {suppliers.map((supplier) => (
              <option key={supplier.id} value={supplier.id}>
                {supplier.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Product name
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
          />
        </label>
        <label>
          Category
          <select
            value={category}
            onChange={(event) => setCategory(event.target.value)}
          >
            <option>Fertilizers</option>
            <option>Chemicals</option>
            <option>Seeds</option>
            <option>Equipment</option>
            <option>Irrigation</option>
            <option>Other</option>
          </select>
        </label>
        <label>
          Crop suitability (comma separated)
          <input
            value={cropSuitability}
            onChange={(event) => setCropSuitability(event.target.value)}
            placeholder="Rice, Maize"
          />
        </label>
        <label>
          Description
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={3}
          />
        </label>
        <label>
          Unit
          <input value={unit} onChange={(event) => setUnit(event.target.value)} />
        </label>
        <label>
          Pack size
          <input
            value={packSize}
            onChange={(event) => setPackSize(event.target.value)}
          />
        </label>
        <label>
          Price
          <input
            type="number"
            min="0"
            step="0.01"
            value={price}
            onChange={(event) => setPrice(event.target.value)}
          />
        </label>
        <label>
          Availability
          <select
            value={availabilityStatus}
            onChange={(event) =>
              setAvailabilityStatus(
                event.target.value as "available" | "limited" | "out_of_stock",
              )
            }
          >
            <option value="available">available</option>
            <option value="limited">limited</option>
            <option value="out_of_stock">out_of_stock</option>
          </select>
        </label>
        <label className="checkbox-row">
          <input
            type="checkbox"
            checked={active}
            onChange={(event) => setActive(event.target.checked)}
          />
          Active listing
        </label>
        {adminMode && (
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={verified}
              onChange={(event) => setVerified(event.target.checked)}
            />
            Admin verified
          </label>
        )}
        <button className="primary" type="submit">
          Save product
        </button>
      </form>
    </div>
  );
}

function SupplierLoginModal({
  supplierId,
  supplier,
  close,
  saved,
}: {
  supplierId: string;
  supplier?: Supplier;
  close: () => void;
  saved: () => void;
}) {
  const [email, setEmail] = useState(supplier?.email ?? "");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState(
    supplier?.contactName || supplier?.name || "",
  );
  const [error, setError] = useState("");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    try {
      await repository.createSupplierAccount(
        email,
        password,
        supplierId,
        displayName,
        supplier?.phone ?? "",
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
        <span className="eyebrow">SUPPLIER ACCESS</span>
        <h2>Create supplier login</h2>
        <p>
          Creates a secure <code>supplier</code> role account linked to this
          vendor profile.
        </p>
        {error && <div className="notice error">{error}</div>}
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
            minLength={8}
          />
        </label>
        <button className="primary" type="submit">
          Create supplier account
        </button>
      </form>
    </div>
  );
}
