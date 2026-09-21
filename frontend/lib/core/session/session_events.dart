import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 会话过期事件总线。
///
/// 由网络层在"刷新令牌失败、本地凭据已被清空"后自增；认证状态机监听它把状态切换为
/// 未登录，从而让路由守卫把用户送回登录页并提示"登录已过期"。
///
/// 为什么不直接在网络层改认证状态：apiClient → authState → authRepository → apiClient
/// 会构成循环依赖。用一个双方都只依赖的独立事件 Provider 可以解开这个环。
final sessionExpiredProvider = StateProvider<int>((ref) => 0);
