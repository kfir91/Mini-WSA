type Props = {
  text: string;
};

export function LogPanel({ text }: Props) {
  if (!text) {
    return null;
  }
  return <pre className="log-panel">{text}</pre>;
}
