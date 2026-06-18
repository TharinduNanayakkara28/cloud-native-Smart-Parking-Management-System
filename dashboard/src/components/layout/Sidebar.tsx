import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Map,
  DollarSign,
  AlertTriangle,
  Activity,
} from 'lucide-react';

const NAV = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard', end: true },
  { to: '/occupancy', icon: Map, label: 'Occupancy' },
  { to: '/revenue', icon: DollarSign, label: 'Revenue' },
  { to: '/violations', icon: AlertTriangle, label: 'Violations' },
  { to: '/events', icon: Activity, label: 'Events' },
];

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function Sidebar({ open, onClose }: Props) {
  return (
    <>
      {/* Mobile overlay */}
      {open && (
        <div
          className="fixed inset-0 bg-black/40 z-20 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={`
          fixed lg:static inset-y-0 left-0 z-30 w-60 bg-slate-900 text-white
          flex flex-col shrink-0 transition-transform duration-200
          ${open ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
      >
        {/* Logo */}
        <div className="flex items-center gap-3 px-5 py-4 border-b border-slate-700/60">
          <div className="w-8 h-8 bg-blue-500 rounded-lg flex items-center justify-center font-bold text-sm">
            P
          </div>
          <div className="min-w-0">
            <p className="font-semibold text-sm leading-tight truncate">
              Smart Parking
            </p>
            <p className="text-xs text-slate-400 truncate">
              Operator Dashboard
            </p>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-3 py-4 space-y-0.5 overflow-y-auto">
          {NAV.map(({ to, icon: Icon, label, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }
            >
              <Icon size={17} />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="px-5 py-3 border-t border-slate-700/60 text-xs text-slate-500">
          v1.0.0
        </div>
      </aside>
    </>
  );
}
