type Props = {
  title: string;
  children?: React.ReactNode;
};

export function SectionHead({ title, children }: Props) {
  return (
    <div className="section-head">
      <h2>{title}</h2>
      {children ? <p>{children}</p> : null}
    </div>
  );
}
