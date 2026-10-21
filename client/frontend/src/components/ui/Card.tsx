type Props = {
  title?: string;
  spaced?: boolean;
  children: React.ReactNode;
  ariaLabel?: string;
};

export function Card({ title, spaced, children, ariaLabel }: Props) {
  return (
    <section className={`card${spaced ? ' card--spaced' : ''}`} aria-label={ariaLabel}>
      {title ? <h3>{title}</h3> : null}
      {children}
    </section>
  );
}
