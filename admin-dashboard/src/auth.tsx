import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
  type User,
} from "firebase/auth";
import { auth } from "./firebase";
import { repository } from "./repository";
import type { UserAccess } from "./types";

interface AuthState {
  user: User | null;
  access: UserAccess | null;
  loading: boolean;
  error: string;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  sendReset: (email: string) => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [access, setAccess] = useState<UserAccess | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(
    () =>
      onAuthStateChanged(auth, async (nextUser) => {
        setLoading(true);
        setError("");
        setUser(nextUser);
        if (!nextUser) {
          setAccess(null);
          setLoading(false);
          return;
        }
        try {
          setAccess(await repository.getAccess(nextUser.uid));
        } catch (reason) {
          setError(
            reason instanceof Error
              ? reason.message
              : "Unable to verify access.",
          );
          setAccess(null);
        } finally {
          setLoading(false);
        }
      }),
    [],
  );

  const value = useMemo<AuthState>(
    () => ({
      user,
      access,
      loading,
      error,
      login: async (email, password) => {
        setError("");
        await signInWithEmailAndPassword(auth, email, password);
      },
      logout: () => signOut(auth),
      sendReset: (email) => sendPasswordResetEmail(auth, email),
    }),
    [access, error, loading, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
