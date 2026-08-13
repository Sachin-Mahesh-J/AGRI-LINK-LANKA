import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import {
  AlertTriangle,
  BarChart3,
  Bell,
  Boxes,
  Building2,
  Camera,
  ChevronRight,
  ClipboardList,
  Download,
  LayoutDashboard,
  Leaf,
  LogOut,
  Menu,
  Plus,
  RadioTower,
  Search,
  ShoppingBasket,
  Sprout,
  Store,
  Trash2,
  Users,
  X,
} from "lucide-react";
import {
  BrowserRouter,
  Navigate,
  NavLink,
  Outlet,
  Route,
  Routes,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { MapContainer, Marker, Popup, TileLayer } from "react-leaflet";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { AuthProvider, useAuth } from "./auth";
import { firebaseConfigured } from "./firebase";
import { MarketplacePage } from "./MarketplacePage";
import { HarvestMarketplacePage } from "./HarvestMarketplacePage";
import { repository } from "./repository";
import type {
  AccessRole,
  CameraCapture,
  DocDate,
  Farm,
  InventoryItem,
  IoTDevice,
  SensorReading,
  Supplier,
  SupplierStatus,
  UserAccess,
} from "./types";
import { useData } from "./useData";

function farmNameById(farms: Farm[] | null | undefined, farmId?: string | null) {
  if (!farmId) return "—";
  return farms?.find((farm) => farm.id === farmId)?.farmName ?? farmId;
}

function readingTime(value: DocDate | undefined) {
  if (!value) return 0;
  if (typeof value === "number") return value;
  if (typeof value === "string") return new Date(value).getTime() || 0;
  if (typeof value === "object" && "toDate" in value && typeof value.toDate === "function") {
    return value.toDate().getTime();
  }
  return 0;
}

const adminNav = [
  { to: "/", label: "Overview", icon: LayoutDashboard },
  { to: "/users", label: "Users", icon: Users },
  { to: "/farms", label: "Farms", icon: Sprout },
  { to: "/operations", label: "Reports & visits", icon: ClipboardList },
  { to: "/inventory", label: "Inventory", icon: Boxes },
  { to: "/marketplace", label: "Marketplace", icon: Store },
  { to: "/harvest-marketplace", label: "Harvest market", icon: ShoppingBasket },
  { to: "/iot", label: "IoT monitoring", icon: RadioTower },
  { to: "/alerts", label: "Alerts", icon: Bell },
  { to: "/reports", label: "Analytics", icon: BarChart3 },
];

const supplierNav = [
  { to: "/marketplace", label: "Marketplace", icon: Store },
];

const buyerNav = [
  { to: "/harvest-marketplace", label: "Harvest market", icon: ShoppingBasket },
];

const isAdminRole = (role?: string | null) =>
  ["admin", "super_admin"].includes(role ?? "");
const isDashboardRole = (role?: string | null) =>
  isAdminRole(role) || role === "supplier" || role === "buyer";

export const dateText = (value: DocDate | undefined) => {
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
  children: ReactNode;
}) {
  if (loading)
    return (
      <div className="state">
        <span className="spinner" /> Loading data…
      </div>
    );
  if (error)
    return (
      <div className="state error">
        <AlertTriangle size={20} /> {error}
      </div>
    );
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
  action?: ReactNode;
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

function Login() {
  const { login, user, access, loading, error } = useAuth();
  const [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [submitError, setSubmitError] = useState("");
  if (
    !loading &&
    user &&
    access?.status === "active" &&
    isDashboardRole(access.role)
  )
    return (
      <Navigate
        to={
          access.role === "supplier"
            ? "/marketplace"
            : access.role === "buyer"
              ? "/harvest-marketplace"
              : "/"
        }
        replace
      />
    );
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitError("");
    try {
      await login(email, password);
    } catch (reason) {
      setSubmitError(
        reason instanceof Error ? reason.message : "Sign in failed.",
      );
    }
  };
  return (
    <main className="login-page">
      <section className="login-brand">
        <div className="brand-mark">
          <Leaf />
        </div>
        <p>AgriScout command center</p>
        <h1>Make every field decision count.</h1>
        <p>
          Monitor farms, field teams, inventory, and connected sensors from one
          secure workspace.
        </p>
      </section>
      <section className="login-panel">
        <form className="login-card" onSubmit={submit}>
          <span className="eyebrow">OPERATIONS PORTAL</span>
          <h2>Welcome back</h2>
          <p>Sign in with your authorized AgriScout admin or supplier account.</p>
          {!firebaseConfigured && (
            <div className="notice">
              Add Firebase values to your local <code>.env</code> before signing
              in.
            </div>
          )}
          {(submitError || error) && (
            <div className="notice error">{submitError || error}</div>
          )}
          {user && !access && !loading && (
            <div className="notice error">
              This account has no administrator access record.
            </div>
          )}
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <button className="primary" disabled={loading}>
            {loading ? "Verifying…" : "Sign in securely"}
          </button>
        </form>
      </section>
    </main>
  );
}

function Guard() {
  const { user, access, loading } = useAuth();
  if (loading)
    return (
      <div className="fullscreen">
        <span className="spinner" /> Verifying administrator access…
      </div>
    );
  if (!user || !access || access.status !== "active" || !isDashboardRole(access.role))
    return <Navigate to="/login" replace />;
  return <Outlet />;
}

function AdminOnly() {
  const { access } = useAuth();
  if (!isAdminRole(access?.role)) {
    if (access?.role === "buyer")
      return <Navigate to="/harvest-marketplace" replace />;
    return <Navigate to="/marketplace" replace />;
  }
  return <Outlet />;
}

function MarketplaceRoute() {
  const { access } = useAuth();
  if (access?.role === "buyer")
    return <Navigate to="/harvest-marketplace" replace />;
  return (
    <MarketplacePage
      supplierScopeId={
        access?.role === "supplier" ? (access.supplierId ?? null) : null
      }
    />
  );
}

function HarvestMarketplaceRoute() {
  const { access } = useAuth();
  if (access?.role === "supplier")
    return <Navigate to="/marketplace" replace />;
  return (
    <HarvestMarketplacePage
      buyerScopeId={access?.role === "buyer" ? (access.buyerId ?? null) : null}
    />
  );
}

function Shell() {
  const { user, access, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const nav =
    access?.role === "supplier"
      ? supplierNav
      : access?.role === "buyer"
        ? buyerNav
        : adminNav;
  return (
    <div className="shell">
      <aside className={open ? "sidebar open" : "sidebar"}>
        <div className="brand">
          <div className="brand-mark">
            <Leaf />
          </div>
          <div>
            <strong>AgriScout</strong>
            <span>
              {access?.role === "supplier"
                ? "SUPPLIER PORTAL"
                : access?.role === "buyer"
                  ? "BUYER PORTAL"
                  : "ADMIN CONSOLE"}
            </span>
          </div>
          <button className="close-nav" onClick={() => setOpen(false)}>
            <X />
          </button>
        </div>
        <nav>
          {nav.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === "/"}
              onClick={() => setOpen(false)}
            >
              <Icon size={19} />
              {label}
              <ChevronRight className="chevron" size={16} />
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="avatar">
            {(user?.email?.[0] ?? "A").toUpperCase()}
          </div>
          <div>
            <strong>
              {access?.displayName ??
                (access?.role === "supplier" ? "Supplier" : "Administrator")}
            </strong>
            <span>{user?.email}</span>
          </div>
          <button aria-label="Sign out" onClick={() => logout()}>
            <LogOut size={18} />
          </button>
        </div>
      </aside>
      {open && (
        <button
          aria-label="Close menu"
          className="backdrop"
          onClick={() => setOpen(false)}
        />
      )}
      <section className="workspace">
        <header className="topbar">
          <button className="menu" onClick={() => setOpen(true)}>
            <Menu />
          </button>
          <div>
            <span>AgriScout Operations</span>
            <strong>
              {access?.role === "supplier"
                ? "Supplier marketplace"
                : "Live administration"}
            </strong>
          </div>
          <div className="live">
            <i /> Systems connected
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </section>
    </div>
  );
}

function Kpi({
  label,
  value,
  note,
  icon,
  tone,
}: {
  label: string;
  value: number;
  note: string;
  icon: ReactNode;
  tone: string;
}) {
  return (
    <article className="kpi">
      <div className={`kpi-icon ${tone}`}>{icon}</div>
      <span>{label}</span>
      <strong>{value.toLocaleString()}</strong>
      <p>{note}</p>
    </article>
  );
}

function Overview() {
  const result = useData(async () => {
    const [
      users,
      farms,
      requests,
      alerts,
      readings,
      inventory,
      devices,
      visits,
    ] = await Promise.all([
      repository.users(),
      repository.farms(),
      repository.requests(),
      repository.alerts(),
      repository.readings(),
      repository.inventory(),
      repository.devices(),
      repository.visits(),
    ]);
    return {
      users,
      farms,
      requests,
      alerts,
      readings,
      inventory,
      devices,
      visits,
    };
  });
  const data = result.data;
  const chart = useMemo(
    () =>
      (data?.readings ?? []).slice(-20).map((r, i) => ({
        name: i + 1,
        moisture: r.soilMoisturePercent ?? 0,
        humidity: r.humidityPercent ?? 0,
      })),
    [data],
  );
  const lowStock = (data?.inventory ?? []).filter(
    (item) => (item.quantity ?? 0) <= (item.reorderLevel ?? 0),
  ).length;
  const criticalDevices = (data?.devices ?? []).filter(
    (device) =>
      ["offline", "inactive"].includes(String(device.status ?? "offline")) ||
      Boolean(device.faultStatus),
  ).length;
  const visitCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const visit of data?.visits ?? []) {
      const uid = visit.officerUid;
      if (uid) counts.set(uid, (counts.get(uid) ?? 0) + 1);
    }
    return counts;
  }, [data?.visits]);
  return (
    <>
      <PageHeader
        eyebrow="COMMAND CENTER"
        title="Good morning, Administrator"
        action={
          <span className="date-pill">
            {new Date().toLocaleDateString(undefined, { dateStyle: "long" })}
          </span>
        }
      />
      <State {...result} empty={!data}>
        <div className="kpi-grid">
          <Kpi
            label="Registered users"
            value={data?.users.length ?? 0}
            note="All access profiles"
            icon={<Users />}
            tone="green"
          />
          <Kpi
            label="Managed farms"
            value={data?.farms.length ?? 0}
            note="Across all officers"
            icon={<Sprout />}
            tone="lime"
          />
          <Kpi
            label="Pending requests"
            value={
              data?.requests.filter(
                (r) => r.status?.toLowerCase() === "pending",
              ).length ?? 0
            }
            note="Awaiting review"
            icon={<Boxes />}
            tone="amber"
          />
          <Kpi
            label="Open alerts"
            value={
              data?.alerts.filter((a) => a.status !== "resolved").length ?? 0
            }
            note="Requires attention"
            icon={<Bell />}
            tone="red"
          />
          <Kpi
            label="Low stock items"
            value={lowStock}
            note="At or below reorder level"
            icon={<Boxes />}
            tone="amber"
          />
          <Kpi
            label="Critical sensors"
            value={criticalDevices}
            note="Offline or fault flagged"
            icon={<RadioTower />}
            tone="red"
          />
        </div>
        <div className="split">
          <section className="card chart-card">
            <div className="card-title">
              <div>
                <span>FIELD CONDITIONS</span>
                <h2>Sensor health trend</h2>
              </div>
              <span className="badge">Latest readings</span>
            </div>
            {chart.length ? (
              <ResponsiveContainer width="100%" height={270}>
                <AreaChart data={chart}>
                  <defs>
                    <linearGradient id="greenFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#20865a" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#20865a" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="name" hide />
                  <YAxis />
                  <Tooltip />
                  <Area
                    type="monotone"
                    dataKey="moisture"
                    stroke="#20865a"
                    fill="url(#greenFill)"
                  />
                  <Line type="monotone" dataKey="humidity" stroke="#d89b2b" />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="state">
                Sensor trends appear when readings arrive.
              </div>
            )}
          </section>
          <section className="card">
            <div className="card-title">
              <div>
                <span>PRIORITY QUEUE</span>
                <h2>Needs attention</h2>
              </div>
            </div>
            <div className="activity">
              {(data?.alerts ?? [])
                .filter((a) => a.status !== "resolved")
                .slice(0, 5)
                .map((a) => (
                  <div key={a.id}>
                    <i className={`severity ${a.severity ?? "medium"}`} />
                    <div>
                      <strong>{a.title ?? "Field alert"}</strong>
                      <p>
                        {a.message ??
                          "Review this alert in the alerts workspace."}
                      </p>
                    </div>
                  </div>
                ))}
              {!data?.alerts.length && (
                <div className="state">No open alerts.</div>
              )}
            </div>
          </section>
        </div>
        <section className="card">
          <div className="card-title">
            <div>
              <span>FIELD TEAM</span>
              <h2>Officer activity</h2>
            </div>
            <span className="badge">
              {data?.users.filter((user) => user.status === "active").length ??
                0}{" "}
              active officers
            </span>
          </div>
          <div className="records">
            {(data?.users ?? []).slice(0, 6).map((user) => (
              <article key={user.id}>
                <div className="record-icon">
                  <Users />
                </div>
                <div>
                  <strong>{user.displayName ?? user.email ?? user.id}</strong>
                  <span>
                    {user.role ?? "field_officer"} ·{" "}
                    {visitCounts.get(user.id) ?? 0} recent visits ·{" "}
                    {user.assignedFarmIds?.length ?? 0} farms
                  </span>
                  <p>
                    {user.status === "pending"
                      ? "Awaiting administrator approval."
                      : user.status === "inactive"
                        ? "Access suspended."
                        : "Active field operations account."}
                  </p>
                </div>
              </article>
            ))}
          </div>
        </section>
      </State>
    </>
  );
}

function UsersPage() {
  const { sendReset, user: currentUser } = useAuth();
  const users = useData(repository.users),
    farms = useData(repository.farms);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<string | null>(null),
    [assignments, setAssignments] = useState<string[]>([]);
  const save = async () => {
    if (selected) {
      await repository.assignFarms(selected, assignments);
      users.reload();
      setSelected(null);
    }
  };
  const toggleStatus = async (user: UserAccess) => {
    await repository.updateUserAccess(user.id, {
      role: user.role ?? "field_officer",
      status: user.status === "active" ? "inactive" : "active",
    });
    users.reload();
  };
  const updateRole = async (user: UserAccess, role: AccessRole) => {
    await repository.updateUserAccess(user.id, {
      role,
      status: user.status ?? "active",
    });
    users.reload();
  };
  const approvePending = async (user: UserAccess) => {
    await repository.updateUserAccess(user.id, {
      role: user.role ?? "field_officer",
      status: "active",
    });
    users.reload();
  };
  return (
    <>
      <PageHeader
        eyebrow="PEOPLE & ACCESS"
        title="Users and assignments"
        action={
          <button className="primary compact" onClick={() => setCreating(true)}>
            <Plus size={17} /> Create officer
          </button>
        }
      />
      <State
        loading={users.loading || farms.loading}
        error={users.error || farms.error}
        empty={!users.data?.length}
      >
        <section className="card table-card">
          <div className="toolbar">
            <div className="search">
              <Search size={17} />
              <input placeholder="Search users" />
            </div>
            <span className="badge">{users.data?.length ?? 0} profiles</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>User</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Assigned farms</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {users.data?.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <strong>{user.displayName ?? "Unnamed user"}</strong>
                      <small>{user.email ?? user.id}</small>
                    </td>
                    <td>
                      <select
                        disabled={user.id === currentUser?.uid}
                        value={user.role ?? "field_officer"}
                        onChange={(event) =>
                          updateRole(user, event.target.value as AccessRole)
                        }
                      >
                        <option value="field_officer">Field officer</option>
                        <option value="supplier">Supplier</option>
                        <option value="buyer">Buyer</option>
                        <option value="admin">Admin</option>
                      </select>
                    </td>
                    <td>
                      <span
                        className={
                          user.status === "active" ? "status" : "status off"
                        }
                      >
                        {user.status === "pending"
                          ? "Pending approval"
                          : user.status === "inactive"
                            ? "Inactive"
                            : "Active"}
                      </span>
                    </td>
                    <td>{user.assignedFarmIds?.length ?? 0}</td>
                    <td className="actions">
                      {user.status === "pending" && (
                        <button
                          className="text-button"
                          onClick={() => approvePending(user)}
                        >
                          Approve
                        </button>
                      )}
                      <button
                        className="text-button"
                        onClick={() => {
                          setSelected(user.id);
                          setAssignments(user.assignedFarmIds ?? []);
                        }}
                      >
                        Assign
                      </button>
                      {user.id !== currentUser?.uid && (
                        <button
                          className="text-button"
                          onClick={() => toggleStatus(user)}
                        >
                          {user.status === "active" ? "Deactivate" : "Activate"}
                        </button>
                      )}
                      {user.email && (
                        <button
                          className="text-button"
                          onClick={() => sendReset(user.email!)}
                        >
                          Reset access
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
      {selected && (
        <div className="modal-bg">
          <section className="modal">
            <button className="modal-close" onClick={() => setSelected(null)}>
              <X />
            </button>
            <span className="eyebrow">FARM ACCESS</span>
            <h2>Update assignments</h2>
            <div className="check-list">
              {farms.data?.map((farm) => (
                <label key={farm.path ?? farm.id}>
                  <input
                    type="checkbox"
                    checked={assignments.includes(farm.id)}
                    onChange={(e) =>
                      setAssignments(
                        e.target.checked
                          ? [...assignments, farm.id]
                          : assignments.filter((id) => id !== farm.id),
                      )
                    }
                  />
                  <span>
                    {farm.farmName ?? "Unnamed farm"}
                    <small>{farm.locationText ?? "No location"}</small>
                  </span>
                </label>
              ))}
            </div>
            <button className="primary" onClick={save}>
              Save assignments
            </button>
          </section>
        </div>
      )}
      {creating && (
        <OfficerModal
          close={() => setCreating(false)}
          saved={() => {
            setCreating(false);
            users.reload();
          }}
        />
      )}
    </>
  );
}

function OfficerModal({
  close,
  saved,
}: {
  close: () => void;
  saved: () => void;
}) {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    try {
      await repository.createOfficer(email, password, displayName, phone);
      saved();
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "Unable to create officer.",
      );
    }
  };
  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">FIELD TEAM</span>
        <h2>Create field officer</h2>
        {error && <div className="notice error">{error}</div>}
        <label>
          Name
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
          Phone
          <input
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
          />
        </label>
        <label>
          Temporary password
          <input
            type="password"
            minLength={6}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
        <button className="primary">Create officer</button>
      </form>
    </div>
  );
}

function FarmsPage() {
  const farms = useData(repository.farms);
  const users = useData(repository.users);
  const devices = useData(repository.devices);
  const [search, setSearch] = useState(""),
    [district, setDistrict] = useState("all");
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();
  const officers = (users.data ?? []).filter(
    (user) =>
      user.role === "field_officer" &&
      (user.status === "active" || !user.status),
  );
  const districts = [
    ...new Set(
      (farms.data ?? [])
        .map((f) => f.locationText)
        .filter((value): value is string => Boolean(value)),
    ),
  ];
  const filtered = (farms.data ?? [])
    .filter((farm) => district === "all" || farm.locationText === district)
    .filter((farm) =>
      `${farm.farmName} ${farm.cropType} ${farm.locationText}`
        .toLowerCase()
        .includes(search.toLowerCase()),
    );
  const deviceByHardwareId = new Map(
    (devices.data ?? [])
      .filter((device) => device.deviceId)
      .map((device) => [device.deviceId as string, device]),
  );
  return (
    <>
      <PageHeader
        eyebrow="LAND OPERATIONS"
        title="Managed farms"
        action={
          <button className="primary compact" onClick={() => setCreating(true)}>
            <Plus size={17} /> Create farm
          </button>
        }
      />
      <p className="muted" style={{ marginTop: 0, marginBottom: "1rem" }}>
        Field officers add farms in the Android app and sync them here. You can also
        create a farm and assign it to an officer. Link sensor/camera IoT later from
        the farm profile.
      </p>
      <div className="toolbar floating">
        <div className="search">
          <Search size={17} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search farms, crops, locations"
          />
        </div>
        <select value={district} onChange={(e) => setDistrict(e.target.value)}>
          <option value="all">All locations</option>
          {districts.map((item) => (
            <option key={item}>{item}</option>
          ))}
        </select>
      </div>
      <State {...farms} empty={!filtered.length}>
        <div className="farm-grid">
          {filtered.map((farm) => {
            const sensorId =
              farm.assignedSensorDeviceId || farm.assignedDeviceId;
            const cameraId = farm.assignedCameraDeviceId;
            const sensor = sensorId
              ? deviceByHardwareId.get(sensorId)
              : undefined;
            const camera = cameraId
              ? deviceByHardwareId.get(cameraId)
              : undefined;
            return (
              <button
                className="farm-card"
                key={farm.path ?? farm.id}
                onClick={() =>
                  navigate(`/farms/${farm.officerUid}/${farm.id}`)
                }
              >
                <div className="farm-visual">
                  <Sprout />
                  <span className="status">
                    {sensorId && cameraId
                      ? "Farm IoT linked"
                      : sensorId || cameraId
                        ? "Partial IoT"
                        : "No IoT"}
                  </span>
                </div>
                <div>
                  <span>{farm.locationText ?? "Location not set"}</span>
                  <h2>{farm.farmName ?? "Unnamed farm"}</h2>
                  <p>
                    {farm.cropType ?? "Mixed crops"} ·{" "}
                    {farm.landSize ?? "Size not set"}
                  </p>
                  <p>
                    Officer:{" "}
                    {officers.find((o) => o.id === farm.officerUid)?.displayName ||
                      officers.find((o) => o.id === farm.officerUid)?.email ||
                      farm.officerUid ||
                      "—"}
                  </p>
                  <p>
                    Sensor: {sensorId ?? "—"}
                    {sensor?.status ? ` (${sensor.status})` : ""}
                    <br />
                    Camera: {cameraId ?? "—"}
                    {camera?.status ? ` (${camera.status})` : ""}
                  </p>
                </div>
              </button>
            );
          })}
        </div>
      </State>
      {creating && (
        <FarmIoTModal
          officers={officers}
          close={() => setCreating(false)}
          saved={(result) => {
            setCreating(false);
            farms.reload();
            devices.reload();
            navigate(`/farms/${result.officerUid}/${result.farmId}`);
          }}
        />
      )}
    </>
  );
}

function CaptureLightbox({
  capture,
  farmLabel,
  onClose,
  onDelete,
}: {
  capture: CameraCapture;
  farmLabel: string;
  onClose: () => void;
  onDelete?: (capture: CameraCapture) => Promise<void>;
}) {
  const [deleting, setDeleting] = useState(false);

  const handleDelete = async () => {
    if (!onDelete || deleting) return;
    if (
      !confirm(
        "Delete this camera capture? The image file and its dashboard record will be removed.",
      )
    ) {
      return;
    }
    setDeleting(true);
    try {
      await onDelete(capture);
      onClose();
    } catch (error) {
      alert(
        error instanceof Error ? error.message : "Failed to delete capture.",
      );
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="modal-bg" onClick={onClose} role="presentation">
      <div
        className="modal capture-lightbox"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button type="button" className="modal-close" onClick={onClose}>
          <X />
        </button>
        <span className="eyebrow">CAMERA CAPTURE</span>
        <h2>{farmLabel}</h2>
        {capture.imageUrl ? (
          <img src={capture.imageUrl} alt={`Capture ${capture.id}`} />
        ) : (
          <div className="state">No image URL for this capture.</div>
        )}
        <p>
          {dateText(capture.capturedAt)}
          {capture.deviceId ? ` · ${capture.deviceId}` : ""}
          {capture.resolution ? ` · ${capture.resolution}` : ""}
          {capture.diseaseDetected
            ? ` · Disease: ${capture.diseaseDetected}`
            : capture.aiProcessed
              ? " · AI processed"
              : " · Pending AI"}
        </p>
        <div className="actions">
          {capture.imageUrl ? (
            <a
              className="primary compact"
              href={capture.imageUrl}
              target="_blank"
              rel="noreferrer"
            >
              Open full image
            </a>
          ) : null}
          {onDelete ? (
            <button
              type="button"
              className="secondary compact danger"
              onClick={handleDelete}
              disabled={deleting}
            >
              {deleting ? "Deleting…" : "Delete capture"}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function FarmTelemetryPanel({
  farmId,
  farmName,
  officerUid,
  readings,
  captures,
  loading,
  error,
  onCaptureDeleted,
}: {
  farmId: string;
  farmName: string;
  officerUid?: string;
  readings: SensorReading[];
  captures: CameraCapture[];
  loading?: boolean;
  error?: string;
  onCaptureDeleted?: () => void;
}) {
  const [preview, setPreview] = useState<CameraCapture | null>(null);
  const [clearingAll, setClearingAll] = useState(false);
  const farmReadings = useMemo(
    () =>
      [...readings]
        .filter((reading) => reading.farmId === farmId)
        .sort((a, b) => readingTime(b.recordedAt) - readingTime(a.recordedAt)),
    [readings, farmId],
  );
  const allFarmCaptures = useMemo(
    () =>
      [...captures]
        .filter((capture) => capture.farmId === farmId)
        .sort((a, b) => readingTime(b.capturedAt) - readingTime(a.capturedAt)),
    [captures, farmId],
  );
  const farmCaptures = allFarmCaptures.slice(0, 12);
  const clearAllCaptures = async () => {
    if (
      !confirm(
        `Clear all camera images for ${farmName}? This permanently removes every capture and its stored image file.`,
      )
    ) {
      return;
    }
    setClearingAll(true);
    try {
      const deleted = await repository.removeAllCameraCaptures({
        farmId,
        officerUid,
      });
      setPreview(null);
      onCaptureDeleted?.();
      alert(`Removed ${deleted} camera capture${deleted === 1 ? "" : "s"}.`);
    } catch (error) {
      alert(
        error instanceof Error
          ? error.message
          : "Failed to clear camera captures.",
      );
    } finally {
      setClearingAll(false);
    }
  };
  const latest = farmReadings[0];
  const chart = [...farmReadings]
    .reverse()
    .slice(-24)
    .map((reading) => ({
      date: dateText(reading.recordedAt),
      temperature: reading.temperatureCelsius,
      humidity: reading.humidityPercent,
      moisture: reading.soilMoisturePercent,
    }));

  return (
    <>
      <section className="card farm-iot-panel">
        <div className="card-title">
          <div>
            <span>LIVE FARM IOT</span>
            <h2>Sensors &amp; camera</h2>
          </div>
          <span className="badge">{farmReadings.length} readings</span>
        </div>
        <State loading={Boolean(loading)} error={error ?? ""} empty={false}>
          {latest ? (
            <div className="iot-metric-grid">
              <article className="iot-metric">
                <span>Soil moisture</span>
                <strong>
                  {latest.soilMoisturePercent != null
                    ? `${Math.round(latest.soilMoisturePercent)}%`
                    : "—"}
                </strong>
              </article>
              <article className="iot-metric">
                <span>Temperature</span>
                <strong>
                  {latest.temperatureCelsius != null
                    ? `${Math.round(latest.temperatureCelsius)}°C`
                    : "—"}
                </strong>
              </article>
              <article className="iot-metric">
                <span>Humidity</span>
                <strong>
                  {latest.humidityPercent != null
                    ? `${Math.round(latest.humidityPercent)}%`
                    : "—"}
                </strong>
              </article>
              <article className="iot-metric">
                <span>Status</span>
                <strong>{latest.status ?? "—"}</strong>
                <small>{dateText(latest.recordedAt)}</small>
              </article>
            </div>
          ) : (
            <div className="state">
              No sensor readings for this farm yet. Once the ESP32 sensor
              ingests, values appear here automatically.
            </div>
          )}

          {chart.length > 1 ? (
            <div className="farm-iot-chart">
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={chart}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="date" hide />
                  <YAxis width={36} />
                  <Tooltip />
                  <Line
                    type="monotone"
                    dataKey="moisture"
                    stroke="#20865a"
                    dot={false}
                    name="Moisture %"
                  />
                  <Line
                    type="monotone"
                    dataKey="temperature"
                    stroke="#df6d3b"
                    dot={false}
                    name="Temp °C"
                  />
                  <Line
                    type="monotone"
                    dataKey="humidity"
                    stroke="#3187c8"
                    dot={false}
                    name="Humidity %"
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : null}

          <div className="table-wrap" style={{ marginTop: "1rem" }}>
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Source</th>
                  <th>Moisture</th>
                  <th>Temp</th>
                  <th>Humidity</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {farmReadings.slice(0, 8).map((reading) => (
                  <tr key={reading.path ?? reading.id}>
                    <td>{dateText(reading.recordedAt)}</td>
                    <td>{reading.source ?? "—"}</td>
                    <td>
                      {reading.soilMoisturePercent != null
                        ? `${Math.round(reading.soilMoisturePercent)}%`
                        : "—"}
                    </td>
                    <td>
                      {reading.temperatureCelsius != null
                        ? `${Math.round(reading.temperatureCelsius)}°C`
                        : "—"}
                    </td>
                    <td>
                      {reading.humidityPercent != null
                        ? `${Math.round(reading.humidityPercent)}%`
                        : "—"}
                    </td>
                    <td>{reading.status ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!farmReadings.length ? (
              <div className="state">No recent samples.</div>
            ) : null}
          </div>
        </State>
      </section>

      <section className="card farm-iot-panel">
        <div className="card-title">
          <div>
            <span>FIELD CAMERA</span>
            <h2>Captures for {farmName}</h2>
          </div>
          <div className="actions">
            {allFarmCaptures.length ? (
              <button
                type="button"
                className="secondary compact danger"
                disabled={clearingAll}
                onClick={clearAllCaptures}
              >
                {clearingAll ? "Clearing…" : "Clear all images"}
              </button>
            ) : null}
            <span className="badge">{allFarmCaptures.length} images</span>
          </div>
        </div>
        {farmCaptures.length ? (
          <div className="capture-grid">
            {farmCaptures.map((capture) => (
              <button
                type="button"
                className="capture-tile"
                key={capture.path ?? capture.id}
                onClick={() => setPreview(capture)}
              >
                {capture.imageUrl ? (
                  <img
                    src={capture.imageUrl}
                    alt={`Capture ${capture.id}`}
                  />
                ) : (
                  <div className="capture-missing">No image</div>
                )}
                <span>{dateText(capture.capturedAt)}</span>
                <small>
                  {capture.deviceId ?? "camera"}
                  {capture.diseaseDetected
                    ? ` · ${capture.diseaseDetected}`
                    : capture.aiProcessed
                      ? " · AI"
                      : ""}
                </small>
              </button>
            ))}
          </div>
        ) : (
          <div className="state">
            No camera captures for this farm yet. After the ESP32-CAM uploads,
            tap an image here to enlarge it.
          </div>
        )}
      </section>

      {preview ? (
        <CaptureLightbox
          capture={preview}
          farmLabel={farmName}
          onClose={() => setPreview(null)}
          onDelete={async (capture) => {
            await repository.removeCameraCapture(capture);
            onCaptureDeleted?.();
          }}
        />
      ) : null}
    </>
  );
}

function FarmDetail() {
  const { officerUid, id } = useParams();
  const navigate = useNavigate();
  const iotRefresh = { refreshIntervalMs: 20_000 };
  const farms = useData(repository.farms);
  const users = useData(repository.users);
  const devices = useData(repository.devices, [], iotRefresh);
  const readings = useData(repository.readings, [], iotRefresh);
  const captures = useData(repository.cameraCaptures, [], iotRefresh);
  const [editingIoT, setEditingIoT] = useState(false);
  const farm = farms.data?.find(
    (item) => item.id === id && item.officerUid === officerUid,
  );
  const officers = (users.data ?? []).filter(
    (user) => user.role === "field_officer",
  );
  const sensorId = farm?.assignedSensorDeviceId || farm?.assignedDeviceId;
  const cameraId = farm?.assignedCameraDeviceId;
  const sensor = (devices.data ?? []).find((d) => d.deviceId === sensorId);
  const camera = (devices.data ?? []).find((d) => d.deviceId === cameraId);
  const position: [number, number] = [
    farm?.latitude ?? 7.8731,
    farm?.longitude ?? 80.7718,
  ];
  return (
    <>
      <PageHeader
        eyebrow="FARM PROFILE"
        title={farm?.farmName ?? "Farm details"}
        action={
          farm ? (
            <div className="header-actions">
              <button
                className="secondary compact"
                onClick={() => navigate(`/iot?farm=${farm.id}`)}
              >
                Open IoT view
              </button>
              <button
                className="primary compact"
                onClick={() => setEditingIoT(true)}
              >
                Configure Farm IoT
              </button>
            </div>
          ) : undefined
        }
      />
      <State {...farms} empty={!farm}>
        {farm && (
          <>
            <div className="split">
              <section className="card detail-list">
                <h2>Field information</h2>
                <dl>
                  <div>
                    <dt>Owner</dt>
                    <dd>{farm.farmerName ?? "—"}</dd>
                  </div>
                  <div>
                    <dt>Crop</dt>
                    <dd>{farm.cropType ?? "—"}</dd>
                  </div>
                  <div>
                    <dt>Location</dt>
                    <dd>{farm.locationText ?? "—"}</dd>
                  </div>
                  <div>
                    <dt>Land size</dt>
                    <dd>{farm.landSize ?? "—"}</dd>
                  </div>
                  <div>
                    <dt>Officer UID</dt>
                    <dd>{farm.officerUid ?? "—"}</dd>
                  </div>
                </dl>
                <h2 style={{ marginTop: "1.25rem" }}>Farm IoT Device</h2>
                <p className="muted" style={{ marginTop: 0 }}>
                  Sensor and camera modules linked to this farm. Live readings
                  and captures are below.
                </p>
                <dl>
                  <div>
                    <dt>Sensor ID</dt>
                    <dd>{sensorId ?? "Not assigned"}</dd>
                  </div>
                  <div>
                    <dt>Sensor status</dt>
                    <dd>{sensor?.status ?? "—"}</dd>
                  </div>
                  <div>
                    <dt>Camera ID</dt>
                    <dd>{cameraId ?? "Not assigned"}</dd>
                  </div>
                  <div>
                    <dt>Camera status</dt>
                    <dd>{camera?.status ?? "—"}</dd>
                  </div>
                </dl>
              </section>
              <section className="card map-card">
                <MapContainer center={position} zoom={11} scrollWheelZoom={false}>
                  <TileLayer
                    attribution="&copy; OpenStreetMap contributors"
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  />
                  <Marker position={position}>
                    <Popup>{farm.farmName}</Popup>
                  </Marker>
                </MapContainer>
              </section>
            </div>
            <FarmTelemetryPanel
              farmId={farm.id}
              farmName={farm.farmName ?? "Farm"}
              officerUid={officerUid}
              readings={readings.data ?? []}
              captures={captures.data ?? []}
              loading={readings.loading || captures.loading}
              error={readings.error || captures.error}
              onCaptureDeleted={() => captures.reload()}
            />
          </>
        )}
      </State>
      {editingIoT && farm && (
        <FarmIoTModal
          officers={officers}
          existing={farm}
          sensorIngestKey={sensor?.ingestKey}
          cameraIngestKey={camera?.ingestKey}
          close={() => setEditingIoT(false)}
          saved={() => {
            setEditingIoT(false);
            farms.reload();
            devices.reload();
          }}
        />
      )}
    </>
  );
}

function FarmIoTModal({
  officers,
  existing,
  sensorIngestKey,
  cameraIngestKey,
  close,
  saved,
}: {
  officers: UserAccess[];
  existing?: Farm;
  sensorIngestKey?: string;
  cameraIngestKey?: string;
  close: () => void;
  saved: (result: {
    farmId: string;
    officerUid: string;
  }) => void;
}) {
  const isConfigure = Boolean(existing);
  const [officerUid, setOfficerUid] = useState(existing?.officerUid ?? "");
  const [farmName, setFarmName] = useState(existing?.farmName ?? "");
  const [farmerName, setFarmerName] = useState(existing?.farmerName ?? "");
  const [cropType, setCropType] = useState(existing?.cropType ?? "");
  const [locationText, setLocationText] = useState(existing?.locationText ?? "");
  const [landSize, setLandSize] = useState(existing?.landSize ?? "");
  const [notes, setNotes] = useState(existing?.notes ?? "");
  const [linkIoTNow, setLinkIoTNow] = useState(isConfigure);
  const [sensorDeviceId, setSensorDeviceId] = useState(
    existing?.assignedSensorDeviceId || existing?.assignedDeviceId || "",
  );
  const [sensorKey, setSensorKey] = useState(sensorIngestKey ?? "");
  const [cameraDeviceId, setCameraDeviceId] = useState(
    existing?.assignedCameraDeviceId ?? "",
  );
  const [cameraKey, setCameraKey] = useState(cameraIngestKey ?? "");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const showIoTFields = isConfigure || linkIoTNow;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const result = await repository.saveFarmWithIoT({
        officerUid,
        farmId: existing?.id,
        farmName,
        farmerName,
        cropType,
        locationText,
        landSize,
        notes,
        linkIoT: showIoTFields,
        sensorDeviceId,
        sensorIngestKey: sensorKey,
        cameraDeviceId,
        cameraIngestKey: cameraKey,
      });
      saved(result);
    } catch (reason: unknown) {
      setError(
        reason instanceof Error ? reason.message : "Unable to save farm.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">
          {isConfigure ? "FARM IOT LINK" : "ADMIN FARM CREATE"}
        </span>
        <h2>{isConfigure ? "Configure Farm IoT" : "Create farm"}</h2>
        <p>
          {isConfigure
            ? "Link the sensor ESP32 and camera ESP32 for this farm, and register their ingest keys."
            : "Assign this farm to a field officer. Officers can also create farms in the Android app — both appear in this list after sync. IoT linking is optional."}
        </p>

        <h3>Farm information</h3>
        <label>
          Field officer
          <select
            value={officerUid}
            onChange={(event) => setOfficerUid(event.target.value)}
            required
            disabled={isConfigure}
          >
            <option value="">Select officer</option>
            {officers.map((officer) => (
              <option key={officer.id} value={officer.id}>
                {officer.displayName || officer.email || officer.id}
              </option>
            ))}
          </select>
        </label>
        <label>
          Farm name
          <input
            value={farmName}
            onChange={(event) => setFarmName(event.target.value)}
            required
          />
        </label>
        <label>
          Owner / farmer name
          <input
            value={farmerName}
            onChange={(event) => setFarmerName(event.target.value)}
            required
          />
        </label>
        <label>
          Crop type
          <input
            value={cropType}
            onChange={(event) => setCropType(event.target.value)}
            required
          />
        </label>
        <label>
          Location
          <input
            value={locationText}
            onChange={(event) => setLocationText(event.target.value)}
            required
          />
        </label>
        <label>
          Land size
          <input
            value={landSize}
            onChange={(event) => setLandSize(event.target.value)}
          />
        </label>
        <label>
          Notes
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={2}
          />
        </label>

        {!isConfigure ? (
          <label className="checkbox">
            <input
              type="checkbox"
              checked={linkIoTNow}
              onChange={(event) => setLinkIoTNow(event.target.checked)}
            />{" "}
            Also link sensor + camera IoT now
          </label>
        ) : null}

        {showIoTFields ? (
          <>
            <h3>IoT device configuration</h3>
            <label>
              Sensor module Device ID
              <input
                value={sensorDeviceId}
                onChange={(event) => setSensorDeviceId(event.target.value)}
                placeholder="ESP32-FARM-001"
                required={showIoTFields}
              />
            </label>
            <label>
              Sensor ingest key
              <input
                value={sensorKey}
                onChange={(event) => setSensorKey(event.target.value)}
                placeholder="demo-key-esp32-farm-001"
                required={showIoTFields}
              />
            </label>
            <label>
              Camera module Device ID
              <input
                value={cameraDeviceId}
                onChange={(event) => setCameraDeviceId(event.target.value)}
                placeholder="ESP32-CAM-001"
                required={showIoTFields}
              />
            </label>
            <label>
              Camera ingest key
              <input
                value={cameraKey}
                onChange={(event) => setCameraKey(event.target.value)}
                placeholder="demo-key-esp32-cam-001"
                required={showIoTFields}
              />
            </label>
          </>
        ) : null}

        {error ? <p className="form-error">{error}</p> : null}
        <button className="primary" disabled={saving}>
          {saving
            ? "Saving…"
            : isConfigure
              ? "Save Farm IoT link"
              : "Create farm"}
        </button>
      </form>
    </div>
  );
}

function RecordList({
  title,
  loading,
  error,
  rows,
}: {
  title: string;
  loading: boolean;
  error: string;
  rows: { id: string; title: string; meta: string; body?: string }[];
}) {
  return (
    <section className="card">
      <div className="card-title">
        <h2>{title}</h2>
        <span className="badge">{rows.length}</span>
      </div>
      <State loading={loading} error={error} empty={!rows.length}>
        <div className="records">
          {rows.map((row) => (
            <article key={row.id}>
              <div className="record-icon">
                <ClipboardList />
              </div>
              <div>
                <strong>{row.title}</strong>
                <span>{row.meta}</span>
                <p>{row.body ?? "No notes supplied."}</p>
              </div>
            </article>
          ))}
        </div>
      </State>
    </section>
  );
}

function OperationsPage() {
  const reports = useData(repository.reports),
    visits = useData(repository.visits),
    users = useData(repository.users);
  const visitCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const visit of visits.data ?? []) {
      const uid = visit.officerUid;
      if (uid) counts.set(uid, (counts.get(uid) ?? 0) + 1);
    }
    return counts;
  }, [visits.data]);
  return (
    <>
      <PageHeader eyebrow="FIELD WORK" title="Reports and visits" />
      <section className="card">
        <div className="card-title">
          <div>
            <span>OFFICER ACTIVITY</span>
            <h2>Recent field coverage</h2>
          </div>
        </div>
        <State {...users} empty={!users.data?.length}>
          <div className="records">
            {(users.data ?? []).map((user) => (
              <article key={user.id}>
                <div className="record-icon">
                  <Users />
                </div>
                <div>
                  <strong>{user.displayName ?? user.email ?? user.id}</strong>
                  <span>
                    {visitCounts.get(user.id) ?? 0} visits ·{" "}
                    {user.assignedFarmIds?.length ?? 0} assigned farms
                  </span>
                  <p>
                    {user.status === "active"
                      ? "Active officer account."
                      : "Inactive or pending account."}
                  </p>
                </div>
              </article>
            ))}
          </div>
        </State>
      </section>
      <div className="split">
        <RecordList
          title="Field reports"
          loading={reports.loading}
          error={reports.error}
          rows={(reports.data ?? []).map((r) => ({
            id: r.path ?? r.id,
            title: r.detectedIssue ?? r.cropType ?? "Field report",
            meta: `${r.severity ?? "Submitted"}${r.detectionConfidence != null ? ` · ${r.detectionConfidence}% certainty` : ""}${r.detectionSource ? ` · ${r.detectionSource}` : ""} · Officer ${r.officerUid ?? "—"} · ${dateText(r.updatedAt ?? r.createdAt)}`,
            body: [r.detectionExplanation, r.recommendation, r.symptoms ?? r.notes]
              .filter(Boolean)
              .join(" · "),
          }))}
        />
        <RecordList
          title="Farm visits"
          loading={visits.loading}
          error={visits.error}
          rows={(visits.data ?? []).map((v) => ({
            id: v.path ?? v.id,
            title: `${v.cropCondition ?? "Recorded"} farm visit`,
            meta: `Officer ${v.officerUid ?? "—"} · ${dateText(v.updatedAt ?? v.createdAt)}`,
            body: v.notes,
          }))}
        />
      </div>
    </>
  );
}

function InventoryPage() {
  const [tab, setTab] = useState<
    "stock" | "suppliers" | "requests" | "transactions"
  >("stock");
  const inventory = useData(repository.inventory),
    suppliers = useData(repository.suppliers),
    requests = useData(repository.requests),
    transactions = useData(repository.transactions);
  const [editor, setEditor] = useState<{
    kind: "inventory" | "suppliers";
    item?: InventoryItem | Supplier;
  } | null>(null);
  const remove = async (kind: "inventory" | "suppliers", id: string) => {
    if (confirm("Delete this record?")) {
      await repository.remove(kind, id);
      (kind === "inventory" ? inventory : suppliers).reload();
    }
  };
  const review = async (path: string | undefined, approve: boolean) => {
    if (!path) return;
    try {
      if (approve) {
        const request = requests.data?.find((item) => item.path === path);
        const matchingItems = (inventory.data ?? []).filter(
          (item) => item.category === request?.itemType,
        );
        const choices = matchingItems
          .map(
            (item) =>
              `${item.id}: ${item.name} (${item.quantity ?? 0} ${item.unit ?? "units"})`,
          )
          .join("\n");
        const selectedItemId = prompt(
          `Select the exact inventory item ID to issue:\n${choices}`,
          matchingItems[0]?.id ?? "",
        );
        if (!selectedItemId) return;
        await repository.approveRequest(path, selectedItemId.trim());
      } else {
        await repository.rejectRequest(path);
      }
      requests.reload();
      inventory.reload();
      transactions.reload();
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Review failed.");
    }
  };
  const issue = async (path: string | undefined) => {
    if (!path) return;
    try {
      await repository.issueRequest(path);
      requests.reload();
      transactions.reload();
    } catch (reason) {
      alert(reason instanceof Error ? reason.message : "Issue failed.");
    }
  };
  return (
    <>
      <PageHeader
        eyebrow="SUPPLY OPERATIONS"
        title="Inventory control"
        action={
          tab !== "requests" &&
          tab !== "transactions" && (
            <button
              className="primary compact"
              onClick={() =>
                setEditor({ kind: tab === "stock" ? "inventory" : "suppliers" })
              }
            >
              <Plus size={17} /> Add {tab === "stock" ? "item" : "supplier"}
            </button>
          )
        }
      />
      <div className="tabs">
        <button
          className={tab === "stock" ? "active" : ""}
          onClick={() => setTab("stock")}
        >
          Stock
        </button>
        <button
          className={tab === "suppliers" ? "active" : ""}
          onClick={() => setTab("suppliers")}
        >
          Suppliers
        </button>
        <button
          className={tab === "requests" ? "active" : ""}
          onClick={() => setTab("requests")}
        >
          Requests{" "}
          <span>
            {requests.data?.filter((r) => r.status?.toLowerCase() === "pending")
              .length ?? 0}
          </span>
        </button>
        <button
          className={tab === "transactions" ? "active" : ""}
          onClick={() => setTab("transactions")}
        >
          Transactions
        </button>
      </div>
      {tab === "stock" && (
        <State {...inventory} empty={!inventory.data?.length}>
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Category</th>
                    <th>Available</th>
                    <th>Reorder at</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {inventory.data?.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <strong>{item.name ?? "Unnamed item"}</strong>
                        <small>{item.sku || item.id}</small>
                      </td>
                      <td>{item.category ?? "—"}</td>
                      <td>
                        <strong
                          className={
                            (item.quantity ?? 0) <= (item.reorderLevel ?? 0)
                              ? "danger"
                              : ""
                          }
                        >
                          {item.quantity ?? 0} {item.unit ?? ""}
                        </strong>
                      </td>
                      <td>{item.reorderLevel ?? 0}</td>
                      <td className="actions">
                        <button
                          className="text-button"
                          onClick={() => setEditor({ kind: "inventory", item })}
                        >
                          Edit
                        </button>
                        <button
                          className="icon-button"
                          onClick={() => remove("inventory", item.id)}
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
      {tab === "suppliers" && (
        <State {...suppliers} empty={!suppliers.data?.length}>
          <div className="supplier-grid">
            {suppliers.data?.map((supplier) => (
              <section className="card supplier" key={supplier.id}>
                <div className="record-icon">
                  <Building2 />
                </div>
                <div>
                  <h2>{supplier.name ?? "Unnamed supplier"}</h2>
                  <p>{supplier.contactName || "No contact"}</p>
                  <span>
                    {supplier.email || supplier.phone || "No contact details"}
                  </span>
                  <span className={`status ${supplier.status === "active" ? "" : "off"}`}>
                    {supplier.status ?? "pending"}
                  </span>
                </div>
                <div className="actions">
                  <button
                    className="text-button"
                    onClick={() =>
                      setEditor({ kind: "suppliers", item: supplier })
                    }
                  >
                    Edit
                  </button>
                  <button
                    className="icon-button"
                    onClick={() => remove("suppliers", supplier.id)}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </section>
            ))}
          </div>
        </State>
      )}
      {tab === "requests" && (
        <State {...requests} empty={!requests.data?.length}>
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Request</th>
                    <th>Officer</th>
                    <th>Quantity</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {requests.data?.map((request) => (
                    <tr key={request.path ?? request.id}>
                      <td>
                        <strong>
                          {request.itemName ??
                            request.itemType ??
                            "Inventory request"}
                        </strong>
                        <small>
                          {dateText(request.requestedAt ?? request.createdAt)}
                        </small>
                      </td>
                      <td>{request.officerUid ?? "—"}</td>
                      <td>{request.quantity ?? 0}</td>
                      <td>
                        <span
                          className={`status ${request.status?.toLowerCase() === "rejected" ? "off" : ""}`}
                        >
                          {request.status ?? "Pending"}
                        </span>
                      </td>
                      <td className="actions">
                        {request.status?.toLowerCase() === "pending" && (
                          <>
                            <button
                              className="text-button"
                              onClick={() => review(request.path, true)}
                            >
                              Approve
                            </button>
                            <button
                              className="text-button danger"
                              onClick={() => review(request.path, false)}
                            >
                              Reject
                            </button>
                          </>
                        )}
                        {["approved"].includes(
                          request.status?.toLowerCase() ?? "",
                        ) && (
                          <button
                            className="text-button"
                            onClick={() => issue(request.path)}
                          >
                            Mark issued
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
      {tab === "transactions" && (
        <State {...transactions} empty={!transactions.data?.length}>
          <section className="card table-card">
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Item</th>
                    <th>Quantity</th>
                    <th>Balance</th>
                    <th>Date</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.data?.map((entry) => (
                    <tr key={entry.id}>
                      <td>
                        <span className="badge">
                          {entry.type ?? "adjustment"}
                        </span>
                      </td>
                      <td>
                        <strong>{entry.inventoryItemId ?? "—"}</strong>
                        <small>{entry.note ?? "Stock movement"}</small>
                      </td>
                      <td>{entry.quantity ?? 0}</td>
                      <td>{entry.balanceAfter ?? "—"}</td>
                      <td>{dateText(entry.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </State>
      )}
      {editor && (
        <CrudModal
          editor={editor}
          close={() => setEditor(null)}
          saved={() => {
            setEditor(null);
            (editor.kind === "inventory" ? inventory : suppliers).reload();
          }}
        />
      )}
    </>
  );
}

function CrudModal({
  editor,
  close,
  saved,
}: {
  editor: { kind: "inventory" | "suppliers"; item?: InventoryItem | Supplier };
  close: () => void;
  saved: () => void;
}) {
  const source = editor.item ?? ({} as InventoryItem | Supplier);
  const [name, setName] = useState(source.name ?? ""),
    [category, setCategory] = useState(
      (source as InventoryItem).category ?? "",
    ),
    [quantity, setQuantity] = useState((source as InventoryItem).quantity ?? 0),
    [reorder, setReorder] = useState(
      (source as InventoryItem).reorderLevel ?? 0,
    ),
    [email, setEmail] = useState((source as Supplier).email ?? ""),
    [phone, setPhone] = useState((source as Supplier).phone ?? ""),
    [supplierStatus, setSupplierStatus] = useState<SupplierStatus>(
      (source as Supplier).status ?? "pending",
    );
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    await repository.save(
      editor.kind,
      source.id,
      editor.kind === "inventory"
        ? {
            name,
            category: category || "Other",
            quantity,
            reorderLevel: reorder,
            unit: "units",
          }
        : { name, email, phone, status: supplierStatus },
    );
    saved();
  };
  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">
          {editor.kind === "inventory" ? "STOCK RECORD" : "SUPPLIER"}
        </span>
        <h2>{source.id ? "Edit" : "Create"} record</h2>
        <label>
          Name
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </label>
        {editor.kind === "inventory" ? (
          <>
            <label>
              Category
              <select
                value={category || "Other"}
                onChange={(e) => setCategory(e.target.value)}
              >
                <option>Fertilizers</option>
                <option>Chemicals</option>
                <option>Seeds</option>
                <option>Equipment</option>
                <option>Other</option>
              </select>
            </label>
            <div className="form-row">
              <label>
                Quantity
                <input
                  type="number"
                  min="0"
                  value={quantity}
                  onChange={(e) => setQuantity(Number(e.target.value))}
                />
              </label>
              <label>
                Reorder level
                <input
                  type="number"
                  min="0"
                  value={reorder}
                  onChange={(e) => setReorder(Number(e.target.value))}
                />
              </label>
            </div>
          </>
        ) : (
          <>
            <label>
              Email
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            <label>
              Phone
              <input value={phone} onChange={(e) => setPhone(e.target.value)} />
            </label>
            <label>
              Status
              <select
                value={supplierStatus}
                onChange={(e) =>
                  setSupplierStatus(e.target.value as SupplierStatus)
                }
              >
                <option value="pending">pending</option>
                <option value="active">active</option>
                <option value="inactive">inactive</option>
                <option value="rejected">rejected</option>
              </select>
            </label>
          </>
        )}
        <button className="primary">Save record</button>
      </form>
    </div>
  );
}

function IoTPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const iotRefresh = { refreshIntervalMs: 20_000 };
  const devices = useData(repository.devices, [], iotRefresh),
    readings = useData(repository.readings, [], iotRefresh),
    captures = useData(repository.cameraCaptures, [], iotRefresh),
    farms = useData(repository.farms, [], iotRefresh);
  const [editing, setEditing] = useState<IoTDevice | "new" | null>(null);
  const [abnormalOnly, setAbnormalOnly] = useState(false);
  const [preview, setPreview] = useState<CameraCapture | null>(null);
  const [clearingAll, setClearingAll] = useState(false);
  const farmFilter = searchParams.get("farm") ?? "all";
  const setFarmFilter = (value: string) => {
    if (value === "all") {
      setSearchParams({});
    } else {
      setSearchParams({ farm: value });
    }
  };
  const isAbnormal = (reading: {
    soilMoisturePercent?: number;
    humidityPercent?: number;
    temperatureCelsius?: number;
    status?: string;
  }) =>
    reading.status?.toLowerCase() === "critical" ||
    reading.status?.toLowerCase() === "warning" ||
    (reading.soilMoisturePercent ?? 100) < 20 ||
    (reading.humidityPercent ?? 0) > 90 ||
    (reading.temperatureCelsius ?? 0) > 38;
  const scopedReadings = (readings.data ?? []).filter(
    (reading) => farmFilter === "all" || reading.farmId === farmFilter,
  );
  const filteredReadings = scopedReadings.filter(
    (reading) => !abnormalOnly || isAbnormal(reading),
  );
  const offlineOrInactive = (devices.data ?? []).filter((device) =>
    ["offline", "inactive"].includes(String(device.status ?? "offline")),
  );
  const scopedDevices = (devices.data ?? []).filter(
    (device) => farmFilter === "all" || device.farmId === farmFilter,
  );
  const filteredCaptures = [...(captures.data ?? [])].filter(
    (capture) => farmFilter === "all" || capture.farmId === farmFilter,
  );
  const recentCaptures = [...filteredCaptures]
    .sort((a, b) => readingTime(b.capturedAt) - readingTime(a.capturedAt))
    .slice(0, 24);
  const clearAllCaptures = async () => {
    const scopeLabel =
      farmFilter === "all"
        ? "every farm"
        : farmNameById(farms.data, farmFilter);
    if (
      !confirm(
        farmFilter === "all"
          ? "Clear ALL camera images from every farm? This permanently removes every capture and its stored image file."
          : `Clear all camera images for ${scopeLabel}? This permanently removes every capture and its stored image file.`,
      )
    ) {
      return;
    }
    setClearingAll(true);
    try {
      const deleted = await repository.removeAllCameraCaptures(
        farmFilter === "all"
          ? undefined
          : {
              farmId: farmFilter,
              officerUid: (farms.data ?? []).find(
                (farm) => farm.id === farmFilter,
              )?.officerUid,
            },
      );
      setPreview(null);
      captures.reload();
      alert(`Removed ${deleted} camera capture${deleted === 1 ? "" : "s"}.`);
    } catch (error) {
      alert(
        error instanceof Error
          ? error.message
          : "Failed to clear camera captures.",
      );
    } finally {
      setClearingAll(false);
    }
  };
  const removeDevice = async (id: string) => {
    if (confirm("Delete this IoT device record?")) {
      await repository.removeDevice(id);
      devices.reload();
    }
  };
  const chart = [...filteredReadings]
    .sort((a, b) => readingTime(a.recordedAt) - readingTime(b.recordedAt))
    .slice(-40)
    .map((r) => ({
      date: dateText(r.recordedAt),
      temperature: r.temperatureCelsius,
      humidity: r.humidityPercent,
      moisture: r.soilMoisturePercent,
    }));
  const recentReadings = [...filteredReadings]
    .sort((a, b) => readingTime(b.recordedAt) - readingTime(a.recordedAt))
    .slice(0, 12);
  const latestReading = recentReadings[0];
  const stations = Object.values(
    (devices.data ?? []).reduce<
      Record<
        string,
        {
          farmId: string;
          farmName: string;
          officerUid?: string;
          sensor?: IoTDevice;
          camera?: IoTDevice;
        }
      >
    >((acc, device) => {
      const key = device.farmId || "unassigned";
      if (!acc[key]) {
        const farm = (farms.data ?? []).find((item) => item.id === device.farmId);
        acc[key] = {
          farmId: key,
          farmName:
            farm?.farmName ?? (key === "unassigned" ? "Unassigned" : key),
          officerUid: farm?.officerUid ?? device.officerUid,
        };
      }
      if (device.deviceType === "camera") acc[key].camera = device;
      else acc[key].sensor = device;
      return acc;
    }, {}),
  );
  const selectedFarmLabel =
    farmFilter === "all"
      ? "All farms"
      : farmNameById(farms.data, farmFilter);

  return (
    <>
      <PageHeader
        eyebrow="CONNECTED AGRICULTURE"
        title="IoT monitoring"
        action={
          <button className="primary compact" onClick={() => setEditing("new")}>
            <Plus size={17} /> Register device
          </button>
        }
      />
      <section className="card" style={{ marginBottom: "1rem" }}>
        <div className="card-title">
          <div>
            <span>FARM IOT DEVICES</span>
            <h2>Browse sensors &amp; camera by farm</h2>
          </div>
          <span className="badge">{stations.length} farms</span>
        </div>
        <p>
          Pick a farm to focus readings and captures. You can also open a farm
          profile for the same live view. Link hardware from Configure Farm IoT.
        </p>
      </section>
      <div className="filter-bar no-print">
        <label>
          Farm
          <select
            value={farmFilter}
            onChange={(event) => setFarmFilter(event.target.value)}
          >
            <option value="all">All farms</option>
            {(farms.data ?? []).map((farm) => (
              <option key={farm.path ?? farm.id} value={farm.id}>
                {farm.farmName ?? farm.id}
              </option>
            ))}
          </select>
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={abnormalOnly}
            onChange={(event) => setAbnormalOnly(event.target.checked)}
          />{" "}
          Abnormal readings only
        </label>
        <span className="badge">{filteredReadings.length} samples</span>
        <span className="badge">{recentCaptures.length} captures</span>
        <span className="badge">
          {offlineOrInactive.length} offline / inactive
        </span>
      </div>

      {farmFilter !== "all" && latestReading ? (
        <div className="iot-metric-grid" style={{ marginBottom: "1rem" }}>
          <article className="iot-metric">
            <span>Soil moisture</span>
            <strong>
              {latestReading.soilMoisturePercent != null
                ? `${Math.round(latestReading.soilMoisturePercent)}%`
                : "—"}
            </strong>
          </article>
          <article className="iot-metric">
            <span>Temperature</span>
            <strong>
              {latestReading.temperatureCelsius != null
                ? `${Math.round(latestReading.temperatureCelsius)}°C`
                : "—"}
            </strong>
          </article>
          <article className="iot-metric">
            <span>Humidity</span>
            <strong>
              {latestReading.humidityPercent != null
                ? `${Math.round(latestReading.humidityPercent)}%`
                : "—"}
            </strong>
          </article>
          <article className="iot-metric">
            <span>Latest status · {selectedFarmLabel}</span>
            <strong>{latestReading.status ?? "—"}</strong>
            <small>{dateText(latestReading.recordedAt)}</small>
          </article>
        </div>
      ) : null}

      {stations.length > 0 && (
        <section className="card" style={{ marginBottom: 20 }}>
          <div className="card-title">
            <div>
              <span>FARM STATIONS</span>
              <h2>Tap a farm to focus telemetry</h2>
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Farm</th>
                  <th>Sensor device</th>
                  <th>Camera device</th>
                  <th>Sensor status</th>
                  <th>Camera status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {stations.map((station) => (
                  <tr
                    key={station.farmId}
                    className={
                      farmFilter === station.farmId ? "station-row active" : "station-row"
                    }
                    onClick={() =>
                      station.farmId !== "unassigned"
                        ? setFarmFilter(station.farmId)
                        : undefined
                    }
                  >
                    <td>
                      {station.farmName}
                      <br />
                      <small>{station.farmId}</small>
                    </td>
                    <td>{station.sensor?.deviceId ?? "—"}</td>
                    <td>{station.camera?.deviceId ?? "—"}</td>
                    <td>{station.sensor?.status ?? "—"}</td>
                    <td>{station.camera?.status ?? "—"}</td>
                    <td>
                      {station.officerUid && station.farmId !== "unassigned" ? (
                        <button
                          type="button"
                          className="text-button"
                          onClick={(event) => {
                            event.stopPropagation();
                            navigate(
                              `/farms/${station.officerUid}/${station.farmId}`,
                            );
                          }}
                        >
                          Open farm
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <div className="device-grid">
        {scopedDevices.map((device) => {
          const inactive = ["offline", "inactive"].includes(
            String(device.status ?? "offline"),
          );
          const isCamera = device.deviceType === "camera";
          return (
            <section className="card device" key={device.id}>
              <div className="device-head">
                <div className="record-icon">
                  {isCamera ? <Camera /> : <RadioTower />}
                </div>
                <span className={inactive ? "status off" : "status"}>
                  {device.status ?? "offline"}
                </span>
              </div>
              <h2>{device.name ?? device.deviceId ?? "Field device"}</h2>
              <p>
                {isCamera ? "Camera node" : "Sensor node"} · Farm:{" "}
                {farmNameById(farms.data, device.farmId)}
              </p>
              <span>Last seen {dateText(device.lastSeen)}</span>
              {isCamera ? (
                <span>Last capture {dateText(device.lastCaptureAt)}</span>
              ) : (
                <span>Last reading {dateText(device.lastReadingAt)}</span>
              )}
              <small>
                {device.faultStatus ||
                  `Firmware ${device.firmwareVersion ?? "not recorded"}`}
                {typeof device.batteryPercent === "number"
                  ? ` · Battery ${device.batteryPercent}%`
                  : ""}
                {typeof device.signalStrength === "number"
                  ? ` · Signal ${device.signalStrength}`
                  : ""}
              </small>
              {device.ingestKey ? (
                <small>Ingest key configured</small>
              ) : (
                <small>No ingest key — device writes blocked</small>
              )}
              <div className="actions">
                <button
                  className="text-button"
                  onClick={() => setEditing(device)}
                >
                  Edit
                </button>
                <button
                  className="icon-button"
                  onClick={() => removeDevice(device.id)}
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </section>
          );
        })}
      </div>
      <State
        loading={devices.loading || readings.loading}
        error={devices.error || readings.error}
        empty={!scopedDevices.length && !filteredReadings.length}
      >
        <section className="card chart-card">
          <div className="card-title">
            <div>
              <span>SENSOR TELEMETRY</span>
              <h2>
                {farmFilter === "all"
                  ? "Environmental readings"
                  : `Readings · ${selectedFarmLabel}`}
              </h2>
            </div>
            <span className="badge">{filteredReadings.length} samples</span>
          </div>
          {chart.length ? (
            <ResponsiveContainer width="100%" height={330}>
              <LineChart data={chart}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="date" />
                <YAxis />
                <Tooltip />
                <Line
                  type="monotone"
                  dataKey="temperature"
                  stroke="#df6d3b"
                  dot={false}
                />
                <Line
                  type="monotone"
                  dataKey="humidity"
                  stroke="#3187c8"
                  dot={false}
                />
                <Line
                  type="monotone"
                  dataKey="moisture"
                  stroke="#20865a"
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="state">No sensor readings have synced yet.</div>
          )}
        </section>
        <section className="card">
          <div className="card-title">
            <div>
              <span>RECENT SAMPLES</span>
              <h2>Latest sensor values</h2>
            </div>
          </div>
          {recentReadings.length ? (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Farm</th>
                    <th>Device</th>
                    <th>Source</th>
                    <th>Status</th>
                    <th>Moisture</th>
                    <th>Temp</th>
                    <th>Humidity</th>
                  </tr>
                </thead>
                <tbody>
                  {recentReadings.map((reading) => (
                    <tr key={reading.path ?? reading.id}>
                      <td>{dateText(reading.recordedAt)}</td>
                      <td>{farmNameById(farms.data, reading.farmId)}</td>
                      <td>{reading.deviceId ?? "—"}</td>
                      <td>{reading.source ?? "simulated"}</td>
                      <td>{reading.status ?? "—"}</td>
                      <td>
                        {reading.soilMoisturePercent != null
                          ? `${Math.round(reading.soilMoisturePercent)}%`
                          : "—"}
                      </td>
                      <td>
                        {reading.temperatureCelsius != null
                          ? `${Math.round(reading.temperatureCelsius)}°C`
                          : "—"}
                      </td>
                      <td>
                        {reading.humidityPercent != null
                          ? `${Math.round(reading.humidityPercent)}%`
                          : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="state">No recent readings.</div>
          )}
        </section>
      </State>
      <State
        loading={captures.loading}
        error={captures.error}
        empty={!recentCaptures.length}
      >
        <section className="card">
          <div className="card-title">
            <div>
              <span>FIELD IMAGERY</span>
              <h2>
                {farmFilter === "all"
                  ? "Camera captures"
                  : `Captures · ${selectedFarmLabel}`}
              </h2>
            </div>
            <div className="actions">
              {filteredCaptures.length ? (
                <button
                  type="button"
                  className="secondary compact danger"
                  disabled={clearingAll}
                  onClick={clearAllCaptures}
                >
                  {clearingAll
                    ? "Clearing…"
                    : farmFilter === "all"
                      ? "Clear all images"
                      : "Clear farm images"}
                </button>
              ) : null}
              <span className="badge">{filteredCaptures.length} recent</span>
            </div>
          </div>
          {recentCaptures.length ? (
            <div className="capture-grid">
              {recentCaptures.map((capture) => (
                <button
                  type="button"
                  className="capture-tile"
                  key={capture.path ?? capture.id}
                  onClick={() => setPreview(capture)}
                >
                  {capture.imageUrl ? (
                    <img
                      src={capture.imageUrl}
                      alt={`Capture ${capture.id}`}
                    />
                  ) : (
                    <div className="capture-missing">No image</div>
                  )}
                  <span>{farmNameById(farms.data, capture.farmId)}</span>
                  <small>
                    {dateText(capture.capturedAt)}
                    {capture.resolution ? ` · ${capture.resolution}` : ""}
                    {capture.diseaseDetected
                      ? ` · ${capture.diseaseDetected}`
                      : capture.aiProcessed
                        ? " · AI"
                        : ""}
                  </small>
                </button>
              ))}
            </div>
          ) : (
            <div className="state">
              No camera uploads yet for this filter. Register a camera device and
              wait for the next ESP32-CAM capture.
            </div>
          )}
        </section>
      </State>
      {preview ? (
        <CaptureLightbox
          capture={preview}
          farmLabel={farmNameById(farms.data, preview.farmId)}
          onClose={() => setPreview(null)}
          onDelete={async (capture) => {
            await repository.removeCameraCapture(capture);
            captures.reload();
          }}
        />
      ) : null}
      {editing && (
        <DeviceModal
          device={editing === "new" ? undefined : editing}
          farms={farms.data ?? []}
          close={() => setEditing(null)}
          saved={() => {
            setEditing(null);
            devices.reload();
            farms.reload();
          }}
        />
      )}
    </>
  );
}

function DeviceModal({
  device,
  farms,
  close,
  saved,
}: {
  device?: IoTDevice;
  farms: Farm[];
  close: () => void;
  saved: () => void;
}) {
  const [name, setName] = useState(device?.name ?? "");
  const [deviceId, setDeviceId] = useState(device?.deviceId ?? "");
  const [deviceType, setDeviceType] = useState<"sensor" | "camera">(
    device?.deviceType === "camera" ? "camera" : "sensor",
  );
  const [farmId, setFarmId] = useState(device?.farmId ?? "");
  const [status, setStatus] = useState<IoTDevice["status"]>(
    device?.status ?? "offline",
  );
  const [firmwareVersion, setFirmwareVersion] = useState(
    device?.firmwareVersion ?? "",
  );
  const [ingestKey, setIngestKey] = useState(device?.ingestKey ?? "");
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const selectedFarm = farms.find((farm) => farm.id === farmId);
    const resolvedKey =
      ingestKey.trim() ||
      device?.ingestKey ||
      (typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID().replace(/-/g, "")
        : `key-${Date.now()}`);
    await repository.saveDevice(device?.id, {
      name,
      deviceId,
      deviceType,
      farmId: farmId || null,
      farmPath: selectedFarm?.path || device?.farmPath || null,
      officerUid: selectedFarm?.officerUid || device?.officerUid || null,
      status,
      firmwareVersion,
      ingestKey: resolvedKey,
    });
    setIngestKey(resolvedKey);
    saved();
  };
  return (
    <div className="modal-bg">
      <form className="modal" onSubmit={submit}>
        <button type="button" className="modal-close" onClick={close}>
          <X />
        </button>
        <span className="eyebrow">IOT REGISTRY</span>
        <h2>{device ? "Edit device" : "Register device"}</h2>
        <label>
          Device type
          <select
            value={deviceType}
            onChange={(event) =>
              setDeviceType(event.target.value as "sensor" | "camera")
            }
          >
            <option value="sensor">Sensor node (ESP32)</option>
            <option value="camera">Camera node (ESP32-CAM)</option>
          </select>
        </label>
        <label>
          Name
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
          />
        </label>
        <label>
          Device ID
          <input
            value={deviceId}
            onChange={(event) => setDeviceId(event.target.value)}
            required
          />
        </label>
        <label>
          Linked farm
          <select
            value={farmId}
            onChange={(event) => setFarmId(event.target.value)}
          >
            <option value="">Unassigned</option>
            {farms.map((farm) => (
              <option key={farm.path ?? farm.id} value={farm.id}>
                {farm.farmName ?? farm.id} ({farm.id})
              </option>
            ))}
          </select>
        </label>
        <label>
          Status
          <select
            value={status}
            onChange={(event) =>
              setStatus(event.target.value as IoTDevice["status"])
            }
          >
            <option value="online">Online / active</option>
            <option value="offline">Offline</option>
            <option value="inactive">Inactive</option>
            <option value="maintenance">Maintenance</option>
          </select>
        </label>
        <label>
          Firmware version
          <input
            value={firmwareVersion}
            onChange={(event) => setFirmwareVersion(event.target.value)}
          />
        </label>
        <label>
          Ingest key
          <input
            value={ingestKey}
            onChange={(event) => setIngestKey(event.target.value)}
            placeholder="Auto-generated on save if blank"
          />
        </label>
        <p>
          <small>
            {deviceType === "camera"
              ? "Camera devices POST JPEG payloads to ingestCameraImage with Authorization: Bearer <ingest key> and X-Device-Id. Use a separate device ID / ingest key from the sensor (e.g. ESP32-CAM-001). Link both to the same farm."
              : "Sensor devices POST to ingestSensorReading with Authorization: Bearer <ingest key> and X-Device-Id. Match firmware config.h (e.g. ESP32-FARM-001 / demo-key-esp32-farm-001). Camera nodes stay separate devices on the same farm."}
          </small>
        </p>
        <button className="primary">Save device</button>
      </form>
    </div>
  );
}

function AlertsPage() {
  const alerts = useData(repository.alerts),
    recommendations = useData(repository.recommendations);
  const resolve = async (id: string) => {
    await repository.resolveAlert(id);
    alerts.reload();
  };
  return (
    <>
      <PageHeader eyebrow="ACTION CENTER" title="Alerts and recommendations" />
      <div className="split">
        <section className="card">
          <div className="card-title">
            <h2>Active alerts</h2>
            <span className="badge">
              {alerts.data?.filter((a) => a.status !== "resolved").length ?? 0}{" "}
              open
            </span>
          </div>
          <State {...alerts} empty={!alerts.data?.length}>
            <div className="alert-list">
              {alerts.data?.map((alert) => (
                <article key={alert.id}>
                  <i className={`severity ${alert.severity ?? "medium"}`} />
                  <div>
                    <strong>{alert.title ?? "Field alert"}</strong>
                    <span>
                      {dateText(alert.createdAt)} · {alert.severity ?? "medium"}
                    </span>
                    <p>{alert.message ?? "No details supplied."}</p>
                  </div>
                  {alert.status !== "resolved" && (
                    <button
                      className="text-button"
                      onClick={() => resolve(alert.id)}
                    >
                      Resolve
                    </button>
                  )}
                </article>
              ))}
            </div>
          </State>
        </section>
        <section className="card">
          <div className="card-title">
            <h2>Cultivation recommendations</h2>
            <span className="badge">{recommendations.data?.length ?? 0} items</span>
          </div>
          <State {...recommendations} empty={!recommendations.data?.length}>
            <div className="records">
              {recommendations.data?.map((item) => (
                <article key={item.id}>
                  <div className="record-icon">
                    <Leaf />
                  </div>
                  <div>
                    <strong>{item.title ?? "Crop recommendation"}</strong>
                    <span>
                      {item.type?.replace(/_/g, " ") ?? "Recommendation"}
                      {item.activityStatus ? ` · ${item.activityStatus}` : ""}
                      {item.stage ? ` · ${item.stage.replace(/_/g, " ")}` : ""}
                      {item.priority ? ` · ${item.priority}` : ""}
                      {item.source ? ` · ${item.source}` : ""}
                      {item.confidence != null ? ` · ${item.confidence}% certainty` : ""}
                      {item.officerUid ? ` · ${item.officerUid}` : ""}
                      {" · "}
                      {dateText(item.createdAt)}
                    </span>
                    <p>{item.message ?? "No details supplied."}</p>
                    {(item.issueSignal || item.agriculturalNeed || item.recommendedAction || item.productCategory || item.rationale) && (
                      <p className="muted">
                        {item.issueSignal ? `Issue: ${item.issueSignal}` : ""}
                        {item.agriculturalNeed
                          ? `${item.issueSignal ? " · " : ""}Need: ${item.agriculturalNeed}`
                          : ""}
                        {item.recommendedAction
                          ? `${item.issueSignal || item.agriculturalNeed ? " · " : ""}Action: ${item.recommendedAction}`
                          : ""}
                        {item.productCategory
                          ? `${item.issueSignal || item.agriculturalNeed || item.recommendedAction ? " · " : ""}Category: ${item.productCategory}`
                          : ""}
                        {item.rationale
                          ? `${item.issueSignal || item.agriculturalNeed || item.recommendedAction || item.productCategory ? " · " : ""}Why: ${item.rationale}`
                          : ""}
                      </p>
                    )}
                    {(item.suggestedQuantity != null || item.suggestedItemName) && (
                      <p className="muted">
                        {item.dayOfSeason != null ? `Day ${item.dayOfSeason} · ` : ""}
                        {item.suggestedQuantity != null
                          ? `${item.type === "HARVEST" ? "Est. yield up to" : "Qty"} ${item.suggestedQuantity}${item.quantityUnit ? ` ${item.quantityUnit}` : ""}`
                          : ""}
                        {item.suggestedItemName
                          ? `${item.suggestedQuantity != null ? " · " : ""}In-stock: ${item.suggestedItemName}`
                          : ""}
                        {item.alternativeItemName
                          ? ` · Alternative: ${item.alternativeItemName}`
                          : ""}
                      </p>
                    )}
                    <p className="muted">Decision support estimate — confirm in the field.</p>
                  </div>
                </article>
              ))}
            </div>
          </State>
        </section>
      </div>
    </>
  );
}

function ReportsPage() {
  const reports = useData(repository.reports),
    visits = useData(repository.visits),
    inventory = useData(repository.inventory),
    devices = useData(repository.devices),
    users = useData(repository.users);
  const [from, setFrom] = useState(""),
    [to, setTo] = useState("");
  const inRange = (value: DocDate | undefined) => {
    const text = dateText(value);
    if (text === "—") return !from && !to;
    const date = new Date(text);
    return (
      (!from || date >= new Date(from)) &&
      (!to || date <= new Date(`${to}T23:59:59`))
    );
  };
  const rows = [
    ...(reports.data ?? []).map((r) => ({
      type: "Report",
      title: r.detectedIssue ?? r.cropType ?? "Field report",
      status: `${r.severity ?? "Submitted"}${r.detectionConfidence != null ? ` · ${r.detectionConfidence}%` : ""}${r.detectionSource ? ` · ${r.detectionSource}` : ""}`,
      date: dateText(r.updatedAt ?? r.createdAt),
      rawDate: r.updatedAt ?? r.createdAt,
    })),
    ...(visits.data ?? []).map((v) => ({
      type: "Visit",
      title: `${v.cropCondition ?? "Recorded"} farm visit`,
      status: v.officerUid ?? "Logged",
      date: dateText(v.updatedAt ?? v.createdAt),
      rawDate: v.updatedAt ?? v.createdAt,
    })),
  ].filter((row) => inRange(row.rawDate));
  const lowStock = (inventory.data ?? []).filter(
    (item) => (item.quantity ?? 0) <= (item.reorderLevel ?? 0),
  ).length;
  const offlineDevices = (devices.data ?? []).filter((device) =>
    ["offline", "inactive"].includes(String(device.status ?? "offline")),
  ).length;
  const exportCsv = () => {
    const csv = [
      ["Type", "Title", "Status", "Date"],
      ...rows.map((r) => [r.type, r.title, r.status, r.date]),
    ]
      .map((row) =>
        row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(","),
      )
      .join("\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "agriscout-report.csv";
    a.click();
    URL.revokeObjectURL(url);
  };
  return (
    <>
      <PageHeader
        eyebrow="MANAGEMENT REPORTING"
        title="Operational analytics"
        action={
          <div className="actions no-print">
            <button className="secondary" onClick={exportCsv}>
              <Download size={17} /> Export CSV
            </button>
            <button className="primary compact" onClick={() => window.print()}>
              Print report
            </button>
          </div>
        }
      />
      <div className="filter-bar no-print">
        <label>
          From
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
          />
        </label>
        <label>
          To
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
          />
        </label>
        <span className="badge">{rows.length} records</span>
      </div>
      <State
        loading={
          reports.loading ||
          visits.loading ||
          inventory.loading ||
          devices.loading ||
          users.loading
        }
        error={
          reports.error ||
          visits.error ||
          inventory.error ||
          devices.error ||
          users.error
        }
        empty={!rows.length}
      >
        <section className="card report-sheet">
          <div className="report-summary">
            <Kpi
              label="Field reports"
              value={rows.filter((r) => r.type === "Report").length}
              note="In selected period"
              icon={<ClipboardList />}
              tone="green"
            />
            <Kpi
              label="Farm visits"
              value={rows.filter((r) => r.type === "Visit").length}
              note="In selected period"
              icon={<Sprout />}
              tone="lime"
            />
            <Kpi
              label="Low stock items"
              value={lowStock}
              note="Inventory below reorder"
              icon={<Boxes />}
              tone="amber"
            />
            <Kpi
              label="Offline devices"
              value={offlineDevices}
              note="IoT registry status"
              icon={<RadioTower />}
              tone="red"
            />
            <Kpi
              label="Active officers"
              value={
                users.data?.filter((user) => user.status === "active").length ??
                0
              }
              note="Staff directory"
              icon={<Users />}
              tone="green"
            />
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Record</th>
                  <th>Status</th>
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={`${row.type}-${index}`}>
                    <td>
                      <span className="badge">{row.type}</span>
                    </td>
                    <td>
                      <strong>{row.title}</strong>
                    </td>
                    <td>{row.status}</td>
                    <td>{row.date}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </State>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<Guard />}>
            <Route element={<Shell />}>
              <Route path="marketplace" element={<MarketplaceRoute />} />
              <Route
                path="harvest-marketplace"
                element={<HarvestMarketplaceRoute />}
              />
              <Route element={<AdminOnly />}>
                <Route index element={<Overview />} />
                <Route path="users" element={<UsersPage />} />
                <Route path="farms" element={<FarmsPage />} />
                <Route path="farms/:officerUid/:id" element={<FarmDetail />} />
                <Route path="operations" element={<OperationsPage />} />
                <Route path="inventory" element={<InventoryPage />} />
                <Route path="iot" element={<IoTPage />} />
                <Route path="alerts" element={<AlertsPage />} />
                <Route path="reports" element={<ReportsPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
