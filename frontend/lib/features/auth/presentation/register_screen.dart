import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'auth_provider.dart';

/// 读者自助注册页 (Stage 10-Q)。
///
/// 客户端校验规则与后端 `RegisterRequest` + `PasswordPolicy` **逐条对齐**
/// （用户名 3-50、邮箱格式、口令 ≥8 且同时含字母与数字、昵称 ≤50），
/// 目的是把明显不合规的输入挡在本地，少一次往返；但**后端仍是最终裁决方**，
/// 因此这里不做"后端规则子集之外"的擅自放宽，并把后端返回的中文 message 原样展示
/// （用户名/邮箱重复、口令过弱、自助注册被关闭等都由它给出准确原因）。
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _emailController = TextEditingController();
  final _nicknameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();

  bool _submitting = false;
  bool _obscurePassword = true;
  String? _errorMessage;

  @override
  void dispose() {
    _usernameController.dispose();
    _emailController.dispose();
    _nicknameController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Icon(Icons.person_add_alt_1,
                        size: 56, color: theme.colorScheme.primary),
                    const SizedBox(height: 12),
                    Text('注册读者账号',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineSmall
                            ?.copyWith(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    Text('注册后默认开通读者（STUDENT）权限，馆员与管理员账号由图书馆发放',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodySmall),
                    const SizedBox(height: 24),

                    if (_errorMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.errorContainer,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          children: [
                            Icon(Icons.error_outline,
                                size: 18, color: theme.colorScheme.error),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(_errorMessage!,
                                  style: TextStyle(color: theme.colorScheme.error)),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],

                    TextFormField(
                      controller: _usernameController,
                      enabled: !_submitting,
                      decoration: const InputDecoration(
                        labelText: '用户名 / 学号',
                        prefixIcon: Icon(Icons.badge_outlined),
                        helperText: '3-50 个字符，注册后不可修改',
                      ),
                      validator: (v) {
                        final value = (v ?? '').trim();
                        if (value.isEmpty) return '请输入用户名';
                        if (value.length < 3 || value.length > 50) {
                          return '用户名长度需在 3-50 个字符之间';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 12),

                    TextFormField(
                      controller: _nicknameController,
                      enabled: !_submitting,
                      decoration: const InputDecoration(
                        labelText: '昵称',
                        prefixIcon: Icon(Icons.face_outlined),
                      ),
                      validator: (v) {
                        final value = (v ?? '').trim();
                        if (value.isEmpty) return '请输入昵称';
                        if (value.length > 50) return '昵称不能超过 50 个字符';
                        return null;
                      },
                    ),
                    const SizedBox(height: 12),

                    TextFormField(
                      controller: _emailController,
                      enabled: !_submitting,
                      keyboardType: TextInputType.emailAddress,
                      decoration: const InputDecoration(
                        labelText: '电子邮箱',
                        prefixIcon: Icon(Icons.mail_outline),
                        helperText: '用于找回账号，需独一无二',
                      ),
                      validator: (v) {
                        final value = (v ?? '').trim();
                        if (value.isEmpty) return '请输入电子邮箱';
                        // 与后端 @Email 保持同等强度：本地只挡明显不合法的写法
                        final emailPattern = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');
                        if (!emailPattern.hasMatch(value)) return '电子邮箱格式不合法';
                        return null;
                      },
                    ),
                    const SizedBox(height: 12),

                    TextFormField(
                      controller: _passwordController,
                      enabled: !_submitting,
                      obscureText: _obscurePassword,
                      decoration: InputDecoration(
                        labelText: '登录密码',
                        prefixIcon: const Icon(Icons.lock_outline),
                        helperText: '至少 8 位，且同时包含字母与数字',
                        suffixIcon: IconButton(
                          icon: Icon(_obscurePassword
                              ? Icons.visibility_off_outlined
                              : Icons.visibility_outlined),
                          onPressed: () =>
                              setState(() => _obscurePassword = !_obscurePassword),
                        ),
                      ),
                      validator: (v) {
                        final value = v ?? '';
                        if (value.isEmpty) return '请输入密码';
                        if (value.length < 8 || value.length > 64) {
                          return '密码长度需在 8-64 位之间';
                        }
                        final hasLetter = RegExp(r'[A-Za-z]').hasMatch(value);
                        final hasDigit = RegExp(r'\d').hasMatch(value);
                        if (!hasLetter || !hasDigit) return '密码需同时包含字母与数字';
                        return null;
                      },
                    ),
                    const SizedBox(height: 12),

                    TextFormField(
                      controller: _confirmController,
                      enabled: !_submitting,
                      obscureText: _obscurePassword,
                      decoration: const InputDecoration(
                        labelText: '确认密码',
                        prefixIcon: Icon(Icons.lock_reset_outlined),
                      ),
                      validator: (v) {
                        if ((v ?? '').isEmpty) return '请再次输入密码';
                        if (v != _passwordController.text) return '两次输入的密码不一致';
                        return null;
                      },
                    ),
                    const SizedBox(height: 24),

                    FilledButton(
                      onPressed: _submitting ? null : _submit,
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 14),
                      ),
                      child: _submitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('注册并登录',
                              style: TextStyle(
                                  fontSize: 16, fontWeight: FontWeight.bold)),
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: _submitting ? null : () => context.go('/login'),
                      child: const Text('已有账号？返回登录'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _submit() async {
    setState(() => _errorMessage = null);
    if (!(_formKey.currentState?.validate() ?? false)) {
      return;
    }

    setState(() => _submitting = true);
    final ok = await ref.read(authStateProvider.notifier).register(
          username: _usernameController.text.trim(),
          email: _emailController.text.trim(),
          nickname: _nicknameController.text.trim(),
          password: _passwordController.text,
        );
    if (!mounted) return;
    setState(() => _submitting = false);

    if (ok) {
      // 注册时已自动登录，认证状态变为 authenticated，路由守卫会自动放行到首页
      if (context.mounted) context.go('/');
      return;
    }

    // 失败原因一律取认证状态里的中文提示（后端 message 优先）
    final message = ref.read(authStateProvider).errorMessage;
    setState(() => _errorMessage = message ?? '注册失败，请稍后重试');
  }
}
