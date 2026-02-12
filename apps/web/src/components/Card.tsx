import type { PropsWithChildren } from "react";

type Props = PropsWithChildren<{
  title: string;
  subtitle?: string;
}>;

export function Card({ title, subtitle, children }: Props) {
  return (
    <section className="card">
      <h2>{title}</h2>
      {subtitle ? <p className="muted">{subtitle}</p> : null}
      <div>{children}</div>
    </section>
  );
}
