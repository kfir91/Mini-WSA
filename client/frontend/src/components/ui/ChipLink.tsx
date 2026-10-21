import { Link } from 'react-router-dom';

type Props = {
  to?: string;
  href?: string;
  children: React.ReactNode;
};

export function ChipLink({ to, href, children }: Props) {
  if (to) {
    return (
      <Link className="chip" to={to}>
        {children}
      </Link>
    );
  }
  return (
    <a className="chip" href={href}>
      {children}
    </a>
  );
}
