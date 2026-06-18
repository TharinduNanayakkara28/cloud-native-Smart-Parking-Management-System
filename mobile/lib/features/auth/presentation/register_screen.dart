import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:reactive_forms/reactive_forms.dart';
import '../../../core/auth/auth_notifier.dart';
import '../../../shared/widgets/error_banner.dart';
import '../../../shared/widgets/loading_button.dart';

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  late final FormGroup _form;
  String? _error;

  @override
  void initState() {
    super.initState();
    _form = FormGroup(
      {
        'name': FormControl<String>(
          validators: [Validators.required, Validators.minLength(2)],
        ),
        'email': FormControl<String>(
          validators: [Validators.required, Validators.email],
        ),
        'phone': FormControl<String>(
          validators: [Validators.required, Validators.minLength(9)],
        ),
        'password': FormControl<String>(
          validators: [Validators.required, Validators.minLength(6)],
        ),
        'confirmPassword': FormControl<String>(
          validators: [Validators.required],
        ),
      },
      validators: [
        Validators.mustMatch('password', 'confirmPassword'),
      ],
    );
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

    await ref.read(authNotifierProvider.notifier).register(
          name: _form.control('name').value as String,
          email: _form.control('email').value as String,
          password: _form.control('password').value as String,
          phone: _form.control('phone').value as String,
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
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/login'),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Create Account',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 4),
              Text(
                'Fill in your details to get started',
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
              const SizedBox(height: 24),
              if (_error != null) ...[
                ErrorBanner(message: _error!),
                const SizedBox(height: 16),
              ],
              ReactiveForm(
                formGroup: _form,
                child: Column(
                  children: [
                    ReactiveTextField<String>(
                      formControlName: 'name',
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Full Name',
                        prefixIcon: Icon(Icons.person_outlined),
                      ),
                      validationMessages: {
                        ValidationMessage.required: (_) => 'Name is required',
                        ValidationMessage.minLength: (_) =>
                            'At least 2 characters',
                      },
                    ),
                    const SizedBox(height: 16),
                    ReactiveTextField<String>(
                      formControlName: 'email',
                      keyboardType: TextInputType.emailAddress,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Email',
                        prefixIcon: Icon(Icons.email_outlined),
                      ),
                      validationMessages: {
                        ValidationMessage.required: (_) => 'Email is required',
                        ValidationMessage.email: (_) =>
                            'Enter a valid email address',
                      },
                    ),
                    const SizedBox(height: 16),
                    ReactiveTextField<String>(
                      formControlName: 'phone',
                      keyboardType: TextInputType.phone,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'Phone Number',
                        prefixIcon: Icon(Icons.phone_outlined),
                      ),
                      validationMessages: {
                        ValidationMessage.required: (_) =>
                            'Phone number is required',
                        ValidationMessage.minLength: (_) =>
                            'Enter a valid phone number',
                      },
                    ),
                    const SizedBox(height: 16),
                    ReactiveTextField<String>(
                      formControlName: 'password',
                      obscureText: true,
                      textInputAction: TextInputAction.next,
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
                    const SizedBox(height: 16),
                    ReactiveTextField<String>(
                      formControlName: 'confirmPassword',
                      obscureText: true,
                      textInputAction: TextInputAction.done,
                      onSubmitted: (_) => _submit(),
                      decoration: const InputDecoration(
                        labelText: 'Confirm Password',
                        prefixIcon: Icon(Icons.lock_outlined),
                      ),
                      validationMessages: {
                        ValidationMessage.required: (_) =>
                            'Please confirm your password',
                        ValidationMessage.mustMatch: (_) =>
                            'Passwords do not match',
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              LoadingButton(
                label: 'Create Account',
                isLoading: isLoading,
                onPressed: _submit,
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text('Already have an account?'),
                  TextButton(
                    onPressed: () => context.go('/login'),
                    child: const Text('Sign In'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
