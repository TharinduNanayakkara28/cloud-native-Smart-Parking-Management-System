import { AlertCircle } from 'lucide-react';

export default function ErrorAlert({ message }: { message: string }) {
  return (
    <div className="flex items-center gap-3 bg-red-50 text-red-700 border border-red-200 rounded-lg p-4 text-sm">
      <AlertCircle size={16} className="shrink-0" />
      <span>{message}</span>
    </div>
  );
}
