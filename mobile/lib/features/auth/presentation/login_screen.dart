import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:reactive_forms/reactive_forms.dart';
import '../../../core/auth/auth_notifier.dart';
import '../../../shared/widgets/error_banner.dart';
import '../../../shared/widgets/loading_button.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  late final FormGroup _form;
  String? _error;

  @override
  void initState() {
    super.initState();
    _form = FormGroup({
      'email': FormControl<String>(
        validators: [Validators.required, Validators.email],
      ),
      'password': FormControl<String>(
        validators: [Validators.required, Validators.minLength(6)],
      ),
    });
  }

  @override
  void dispose() {
    _form.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_form.invalid) {
      _form.markAllAsTouched();
      return;
    }
    setState(() => _error = null);

    await ref.read(authNotifierProvider.notifier).login(
          _form.control('email').value as String,
          _form.control('password').value as String,
        );

    if (!mounted) return;
    ref.read(authNotifierProvider).whenOrNull(
          error: (e, _) => setState(() => _error = e.toString()),
        );
  }

  @override
  Widget build(BuildContext context) {
    final isLoading = ref.watch(authNotifierProvider).isLoading;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 40),
                Icon(
                  Icons.local_parking_rounded,
                  size: 56,
                  color: Theme.of(context).colorScheme.primary,
                ),
                const SizedBox(height: 16),
                Text(
                  'Smart Parking',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Sign in to your account',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
                const SizedBox(height: 32),
                if (_error != null) ...[
                  ErrorBanner(message: _error!),
                  const SizedBox(height: 16),
                ],
                ReactiveForm(
                  formGroup: _form,
                  child: Column(
                    children: [
                      ReactiveTextField<String>(
                        formControlName: 'email',
                        keyboardType: TextInputType.emailAddress,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'Email',
                          prefixIcon: Icon(Icons.email_outlined),
                        ),
                        validationMessages: {
                          ValidationMessage.required: (_) =>
                              'Email is required',
                          ValidationMessage.email: (_) =>
                              'Enter a valid email address',
                        },
                      ),
                      const SizedBox(height: 16),
                      ReactiveTextField<String>(
                        formControlName: 'password',
                        obscureText: true,
                        textInputAction: TextInputAction.done,
                        onSubmitted: (_) => _submit(),
                        decoration: const InputDecoration(
                          labelText: 'Password',
                          prefixIcon: Icon(Icons.lock_outlined),
                        ),
                        validationMessages: {
                          ValidationMessage.required: (_) =>
                              'Password is required',
                          ValidationMessage.minLength: (_) =>
                              'At least 6 characters',
                        },
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                LoadingButton(
                  label: 'Sign In',
                  isLoading: isLoading,
                  onPressed: _submit,
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Text("Don't have an account?"),
                    TextButton(
                      onPressed: () => context.go('/register'),
                      child: const Text('Register'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
