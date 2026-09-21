import 'package:flutter/material.dart';

import '../../shared/widgets/app_loading_view.dart';

/// 启动页：认证状态尚未确定（正在读取本地凭据 / 向后端确认）时停留在此页。
///
/// 存在的必要性：冷启动时 AuthStatus 为 initial，若此时直接渲染业务页，
/// 首页会立刻发起受保护接口请求且尚未附带令牌，必然得到 401。
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: SafeArea(
        child: AppLoadingView(message: '正在恢复登录状态...'),
      ),
    );
  }
}
