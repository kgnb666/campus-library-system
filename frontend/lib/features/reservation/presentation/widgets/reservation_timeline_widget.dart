import 'package:flutter/material.dart';
import '../../domain/reservation_model.dart';

/// 预约生命周期可视化流转时间线 Widget (Stage 7-A)
/// 用于直观展示预约状态：排队等待中 -> 到馆待取 -> 履约借出完成 (或取消/超期)
class ReservationTimelineWidget extends StatelessWidget {
  final ReservationModel reservation;

  const ReservationTimelineWidget({
    super.key,
    required this.reservation,
  });

  @override
  Widget build(BuildContext context) {
    final status = reservation.status;
    final isCancelled = status == 'CANCELLED';
    final isExpired = status == 'EXPIRED';

    if (isCancelled || isExpired) {
      return Container(
        margin: const EdgeInsets.symmetric(vertical: 8),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.grey.shade100,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.grey.shade300),
        ),
        child: Row(
          children: [
            Icon(
              isCancelled ? Icons.cancel_outlined : Icons.timer_off_outlined,
              size: 16,
              color: isCancelled ? Colors.grey.shade700 : Colors.red.shade700,
            ),
            const SizedBox(width: 8),
            Text(
              isCancelled ? '此预约已由读者主动取消' : '未在 48 小时保留期内到馆取书，预约已自动失效',
              style: TextStyle(
                fontSize: 12,
                color: isCancelled ? Colors.grey.shade700 : Colors.red.shade700,
              ),
            ),
          ],
        ),
      );
    }

    // 确定当前激活的 Step (0: WAITING, 1: READY, 2: COMPLETED)
    int currentStep = 0;
    if (status == 'READY') {
      currentStep = 1;
    } else if (status == 'COMPLETED') {
      currentStep = 2;
    }

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.blue.shade50.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.blue.shade100),
      ),
      child: Row(
        children: [
          // Step 1: 排队等待
          _buildStep(
            context,
            index: 0,
            currentStep: currentStep,
            icon: Icons.hourglass_top,
            title: '排队中',
            subtitle: status == 'WAITING' ? '排位 #${reservation.queuePosition}' : '已就绪',
          ),
          _buildDivider(context, isCompleted: currentStep >= 1),

          // Step 2: 到馆待取
          _buildStep(
            context,
            index: 1,
            currentStep: currentStep,
            icon: Icons.storefront,
            title: '到馆待取',
            subtitle: status == 'READY'
                ? '${reservation.remainingHoldSeconds != null ? (reservation.remainingHoldSeconds! ~/ 3600) : 48}h保留中'
                : (currentStep > 1 ? '已领书' : '等待还书'),
          ),
          _buildDivider(context, isCompleted: currentStep >= 2),

          // Step 3: 借出履约
          _buildStep(
            context,
            index: 2,
            currentStep: currentStep,
            icon: Icons.task_alt,
            title: '借出完成',
            subtitle: currentStep >= 2 ? '流通中' : '最终状态',
          ),
        ],
      ),
    );
  }

  Widget _buildStep(
    BuildContext context, {
    required int index,
    required int currentStep,
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    final isCurrent = index == currentStep;
    final isDone = index < currentStep;
    Color color;

    if (isCurrent) {
      color = Colors.blue.shade700;
    } else if (isDone) {
      color = Colors.green.shade700;
    } else {
      color = Colors.grey.shade400;
    }

    return Expanded(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 26,
            height: 26,
            decoration: BoxDecoration(
              color: isCurrent || isDone ? color.withValues(alpha: 0.15) : Colors.grey.shade200,
              shape: BoxShape.circle,
              border: Border.all(color: color, width: isCurrent ? 2 : 1),
            ),
            child: Icon(
              isDone ? Icons.check : icon,
              size: 14,
              color: color,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            title,
            style: TextStyle(
              fontSize: 11,
              fontWeight: isCurrent ? FontWeight.bold : FontWeight.w500,
              color: isCurrent ? color : Colors.grey.shade800,
            ),
          ),
          Text(
            subtitle,
            style: TextStyle(
              fontSize: 9,
              color: isCurrent ? color : Colors.grey.shade500,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDivider(BuildContext context, {required bool isCompleted}) {
    return Container(
      width: 24,
      height: 2,
      margin: const EdgeInsets.only(bottom: 18),
      color: isCompleted ? Colors.green.shade400 : Colors.grey.shade300,
    );
  }
}
