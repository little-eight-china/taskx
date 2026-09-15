import { useEffect, useState, type ReactNode } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { api } from "../api";
import type { Health } from "../types";

export function Layout() {
  const [health, setHealth] = useState<Health | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = () => {
      api
        .health()
        .then((value) => {
          if (!cancelled) {
            setHealth(value);
            setHealthError(null);
          }
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            setHealth(null);
            setHealthError(err instanceof Error ? err.message : "无法连接 Admin");
          }
        });
    };
    load();
    const id = window.setInterval(load, 10_000);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  const ok = health?.status === "ok";

  return (
    <div className="shell">
      <nav className="nav">
        <div className="brand">
          <h1>TaskX</h1>
          <p>管理台 · 无鉴权</p>
        </div>
        <div>
          <NavLink to="/" end className={navClass}>
            总览
          </NavLink>
          <NavLink to="/tasks" className={navClass}>
            任务
          </NavLink>
          <NavLink to="/executions" className={navClass}>
            执行记录
          </NavLink>
          <NavLink to="/cluster" className={navClass}>
            集群
          </NavLink>
        </div>
        <div className="health">
          <div>
            <span className={`dot ${ok ? "ok" : "bad"}`} />
            <strong>{ok ? "API 正常" : "API 异常"}</strong>
          </div>
          <div style={{ marginTop: 6 }}>
            MySQL {health?.mysql ?? "—"}
            <br />
            Redis {health?.redis ?? "—"}
            {healthError ? (
              <>
                <br />
                {healthError}
              </>
            ) : null}
          </div>
        </div>
      </nav>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}

function navClass({ isActive }: { isActive: boolean }) {
  return `item${isActive ? " active" : ""}`;
}

export function Banner({ kind, children }: { kind: "error" | "ok"; children: ReactNode }) {
  if (!children) {
    return null;
  }
  return <div className={`banner ${kind}`}>{children}</div>;
}
