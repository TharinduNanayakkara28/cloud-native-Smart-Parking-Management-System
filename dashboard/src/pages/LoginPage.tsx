import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore';
import { api } from '../lib/axios';
import type { LoginResponse, UserProfile } from '../types/api';
import ErrorAlert from '../components/shared/ErrorAlert';

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});
type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const { setTokens, setOperator } = useAuthStore();
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormData>({ resolver: zodResolver(schema) });

  const { mutate: login, isPending, error } = useMutation({
    mutationFn: (data: FormData): Promise<LoginResponse> =>
      api.post('/auth/login', data).then((r) => r.data),
    onSuccess: async (data) => {
      setTokens(data.accessToken, data.refreshToken);
      try {
        const profile: UserProfile = await api
          .get('/users/me')
          .then((r) => r.data);
        setOperator({ id: profile.id, name: profile.name, email: profile.email });
      } catch {
        setOperator({ id: '', name: 'Operator', email: '' });
      }
      navigate('/');
    },
  });

  const errorMessage =
    (error as { response?: { data?: { detail?: string } } })?.response?.data
      ?.detail ?? 'Invalid email or password';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 w-full max-w-sm p-8">
        {/* Brand */}
        <div className="flex items-center gap-3 mb-8">
          <div className="w-10 h-10 bg-blue-600 rounded-xl flex items-center justify-center font-bold text-white text-lg">
            P
          </div>
          <div>
            <p className="font-bold text-gray-900">Smart Parking</p>
            <p className="text-xs text-gray-500">Operator Dashboard</p>
          </div>
        </div>

        <h1 className="text-xl font-semibold text-gray-900 mb-6">Sign in</h1>

        {error && (
          <div className="mb-5">
            <ErrorAlert message={errorMessage} />
          </div>
        )}

        <form
          onSubmit={handleSubmit((data) => login(data))}
          className="space-y-4"
        >
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Email
            </label>
            <input
              type="email"
              {...register('email')}
              placeholder="operator@company.com"
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            />
            {errors.email && (
              <p className="text-xs text-red-500 mt-1">
                {errors.email.message}
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Password
            </label>
            <input
              type="password"
              {...register('password')}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition"
            />
            {errors.password && (
              <p className="text-xs text-red-500 mt-1">
                {errors.password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isPending}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded-lg py-2.5 text-sm font-semibold disabled:opacity-50 disabled:cursor-not-allowed transition-colors mt-2"
          >
            {isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  );
}
