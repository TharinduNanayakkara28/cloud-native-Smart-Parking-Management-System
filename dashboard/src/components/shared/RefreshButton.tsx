import { RefreshCw } from 'lucide-react';
import { useState } from 'react';

interface Props {
  onRefresh: () => Promise<unknown> | unknown;
  label?: string;
}

export default function RefreshButton({ onRefresh, label = 'Refresh' }: Props) {
  const [spinning, setSpinning] = useState(false);

  const handleClick = async () => {
    setSpinning(true);
    try {
      await onRefresh();
    } finally {
      setSpinning(false);
    }
  };

  return (
    <button
      onClick={handleClick}
      disabled={spinning}
      className="flex items-center gap-1.5 text-sm text-gray-600 hover:text-gray-900 border border-gray-300 px-3 py-1.5 rounded-lg hover:bg-gray-50 disabled:opacity-50 transition-colors"
    >
      <RefreshCw size={13} className={spinning ? 'animate-spin' : ''} />
      {label}
    </button>
  );
}
