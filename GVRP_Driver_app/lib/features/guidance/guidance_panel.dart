import 'package:flutter/material.dart';

import '../../l10n/strings_vi.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../routes/route_models.dart';

/// Guidance for the current leg (design §5.3, ui-design §4.4 and §8.2): the
/// server's `instructions_to_next` only, with progressive disclosure. No
/// hand-off to another app.
class GuidanceCard extends StatelessWidget {
  const GuidanceCard({super.key, required this.instructions});

  final List<Instruction> instructions;

  @override
  Widget build(BuildContext context) {
    final first = instructions.isEmpty ? null : instructions.first;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionHeader(
          icon: Icons.assistant_direction_outlined,
          title: S.guidanceTitle,
          trailing: first == null
              ? null
              : OutlinedButton(
                  key: const Key('guidance.allSteps'),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size(48, 36),
                    padding: const EdgeInsets.symmetric(horizontal: 10),
                    visualDensity: VisualDensity.compact,
                  ),
                  onPressed: () => showStepsSheet(context, instructions),
                  child: const Text(S.allSteps, style: TextStyle(fontSize: 13)),
                ),
        ),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(color: AppColors.primaryLight, borderRadius: BorderRadius.circular(8)),
          child: first == null
              ? const Text(
                  S.noGuidance,
                  key: Key('guidance.none'),
                  style: TextStyle(fontSize: 15, color: AppColors.textSecondary),
                )
              : Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _SignIcon(sign: first.sign),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            S.nextLabel.toUpperCase(),
                            style: const TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.5,
                              color: AppColors.primary,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            first.text,
                            key: const Key('guidance.firstStep'),
                            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.text),
                          ),
                          if (_detail(first).isNotEmpty) ...[
                            const SizedBox(height: 2),
                            Text(_detail(first), style: const TextStyle(fontSize: 14, color: AppColors.textSecondary)),
                          ],
                          const SizedBox(height: 4),
                          Text(
                            S.stepCounter(1, instructions.length),
                            style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
        ),
      ],
    );
  }
}

/// Turn icon from the GraphHopper `sign` code; a plain arrow when unknown.
/// Shape only: the text above stays the instruction (ui-design §1.3).
class _SignIcon extends StatelessWidget {
  const _SignIcon({required this.sign});

  final int? sign;

  @override
  Widget build(BuildContext context) {
    final icon = switch (sign) {
      -3 || -2 => Icons.turn_left,
      -1 => Icons.turn_slight_left,
      1 => Icons.turn_slight_right,
      2 || 3 => Icons.turn_right,
      4 => Icons.flag_outlined,
      6 => Icons.roundabout_right,
      _ => Icons.straight,
    };
    return Container(
      width: 40,
      height: 40,
      decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle),
      child: Icon(icon, color: Colors.white, size: 24),
    );
  }
}

String _detail(Instruction i) => [
      formatDistance(i.distanceM),
      if (i.streetName != null && i.streetName!.isNotEmpty) i.streetName!,
    ].where((s) => s.isNotEmpty).join(' · ');

/// Read-only numbered list of all steps of the current leg.
Future<void> showStepsSheet(BuildContext context, List<Instruction> instructions) =>
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: Colors.white,
      builder: (context) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        maxChildSize: 0.9,
        builder: (context, scrollController) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: SectionHeader(icon: Icons.assistant_direction_outlined, title: S.guidanceTitle),
            ),
            Expanded(
              child: ListView.separated(
                key: const Key('guidance.stepsSheet'),
                controller: scrollController,
                itemCount: instructions.length,
                separatorBuilder: (_, _) => const Divider(height: 1, indent: 64),
                itemBuilder: (context, i) {
                  final detail = _detail(instructions[i]);
                  return ListTile(
                    leading: CircleAvatar(
                      radius: 16,
                      backgroundColor: AppColors.primaryLight,
                      foregroundColor: AppColors.primary,
                      child: Text('${i + 1}', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                    ),
                    title: Text(instructions[i].text),
                    subtitle: detail.isEmpty ? null : Text(detail),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
