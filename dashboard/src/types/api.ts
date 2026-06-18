export interface OccupancyResponse {
  date: string;
  totalReservations: number;
  hourlyBreakdown: { hour: string; reservationCount: number }[];
}

export interface RevenueResponse {
  period: string;
  periodStart: string;
  grossRevenue: number;
  refundedAmount: number;
  netRevenue: number;
  transactionCount: number;
}

export interface ViolationsResponse {
  totalViolations: number;
  byTier: { tier: number; type: string; count: number }[];
}

export interface EventRecord {
  id: string;
  eventType: string;
  topic: string;
  userId: string | null;
  entityId: string | null;
  amount: number | null;
  tier: number | null;
  eventTime: string;
  receivedAt: string;
}

export interface SpotModel {
  id: string;
  spotNumber: string;
  floor: string | null;
  latitude: number;
  longitude: number;
  state: 'FREE' | 'RESERVED' | 'OCCUPIED';
  lastUpdated: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface UserProfile {
  id: string;
  name: string;
  email: string;
  phone?: string;
}
