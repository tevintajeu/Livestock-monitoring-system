package com.livestock.vetclient.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.livestock.vetclient.R;
import com.livestock.vetclient.data.model.AnimalProfile;
import com.livestock.vetclient.data.model.TelemetryRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Human-Centered RecyclerView Adapter displaying livestock cards with real cow names,
 * breed/lactation status, plain-language diagnostic summaries, Health-to-Yield insights,
 * and 56dp ergonomic quick-action buttons ("[ 🥛 Log Milk ]" and "[ 🔍 View Health ]").
 */
public class LivestockNodeAdapter extends RecyclerView.Adapter<LivestockNodeAdapter.NodeViewHolder> {

    public interface OnNodeClickListener {
        void onNodeClick(TelemetryRecord record);
        default void onNodeLongClick(TelemetryRecord record, AnimalProfile profile) {}
        default void onLogMilkClick(TelemetryRecord record, AnimalProfile profile) {
            onNodeClick(record);
        }
    }

    private final List<TelemetryRecord> nodeList = new ArrayList<>();
    private final Map<String, AnimalProfile> profileMap = new HashMap<>();
    private final Map<String, Double> todayMilkMap = new HashMap<>();
    private final OnNodeClickListener clickListener;
    private boolean isVetMode = false;

    public LivestockNodeAdapter(OnNodeClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setVetMode(boolean vetMode) {
        this.isVetMode = vetMode;
        notifyDataSetChanged();
    }

    public void updateData(List<TelemetryRecord> newRecords) {
        this.nodeList.clear();
        if (newRecords != null) {
            this.nodeList.addAll(newRecords);
        }
        notifyDataSetChanged();
    }

    public void updateBundle(List<TelemetryRecord> newRecords,
                             Map<String, AnimalProfile> profiles,
                             Map<String, Double> milkMap,
                             boolean vetMode) {
        this.isVetMode = vetMode;
        this.nodeList.clear();
        if (newRecords != null) {
            this.nodeList.addAll(newRecords);
        }
        this.profileMap.clear();
        if (profiles != null) {
            this.profileMap.putAll(profiles);
        }
        this.todayMilkMap.clear();
        if (milkMap != null) {
            this.todayMilkMap.putAll(milkMap);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_livestock_node, parent, false);
        return new NodeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NodeViewHolder holder, int position) {
        TelemetryRecord record = nodeList.get(position);
        AnimalProfile profile = profileMap.get(record.getNodeId());
        if (profile == null) {
            profile = AnimalProfile.createDefaultForNode(record.getNodeId());
        }
        Double todayMilk = todayMilkMap.get(record.getNodeId());
        holder.bind(record, profile, todayMilk, isVetMode, clickListener);
    }

    @Override
    public int getItemCount() {
        return nodeList.size();
    }

    static class NodeViewHolder extends RecyclerView.ViewHolder {

        private final View viewStatusAccent;
        private final TextView tvNodeId;
        private final TextView tvBreedSubtitle;
        private final TextView tvStatusBadge;
        private final LinearLayout layoutPlainSummaryBox;
        private final TextView tvDiagnosticSummary;
        private final TextView tvYieldInsightBanner;
        private final TextView tvTemperature;
        private final ImageView imgTempIcon;
        private final TextView tvAccMagnitude;
        private final ImageView imgMotionIcon;
        private final TextView tvDistance;
        private final ImageView imgDistanceIcon;
        private final TextView tvTimestamp;
        private final TextView tvCardMilkSummary;
        private final MaterialButton btnCardLogMilk;
        private final MaterialButton btnCardViewHealth;

        public NodeViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatusAccent = itemView.findViewById(R.id.view_status_accent_bar);
            tvNodeId = itemView.findViewById(R.id.tv_node_id);
            tvBreedSubtitle = itemView.findViewById(R.id.tv_breed_subtitle);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            layoutPlainSummaryBox = itemView.findViewById(R.id.layout_plain_summary_box);
            tvDiagnosticSummary = itemView.findViewById(R.id.tv_diagnostic_summary);
            tvYieldInsightBanner = itemView.findViewById(R.id.tv_yield_insight_banner);
            tvTemperature = itemView.findViewById(R.id.tv_node_temperature);
            imgTempIcon = itemView.findViewById(R.id.img_temp_icon);
            tvAccMagnitude = itemView.findViewById(R.id.tv_node_acc_mag);
            imgMotionIcon = itemView.findViewById(R.id.img_motion_icon);
            tvDistance = itemView.findViewById(R.id.tv_node_distance);
            imgDistanceIcon = itemView.findViewById(R.id.img_distance_icon);
            tvTimestamp = itemView.findViewById(R.id.tv_node_timestamp);
            tvCardMilkSummary = itemView.findViewById(R.id.tv_card_milk_summary);
            btnCardLogMilk = itemView.findViewById(R.id.btn_card_log_milk);
            btnCardViewHealth = itemView.findViewById(R.id.btn_card_view_health);
        }

        public void bind(final TelemetryRecord record,
                         final AnimalProfile profile,
                         final Double todayMilk,
                         final boolean isVetMode,
                         final OnNodeClickListener listener) {
            Context ctx = itemView.getContext();

            // 1. Bind Friendly Animal Identity Header
            tvNodeId.setText(profile.getDisplayHeader());
            if (isVetMode) {
                tvBreedSubtitle.setText(profile.getBreedAndStatusSubtitle() + " • [" + record.getNodeId() + "]");
            } else {
                tvBreedSubtitle.setText(profile.getBreedAndStatusSubtitle());
            }

            // 2. Bind 3 Visual Metric Pills (Human-Centered vs Vet Mode)
            tvTemperature.setText(record.getHumanTemperatureLabel());
            if (isVetMode) {
                tvAccMagnitude.setText(String.format(Locale.US, "%s (%.1f m/s²)",
                        record.getHumanMotionShortLabel(), record.getDynamicAcceleration()));
            } else {
                tvAccMagnitude.setText("🐾 " + record.getHumanMotionShortLabel());
            }
            tvDistance.setText(record.getHumanDistanceShortLabel());

            // 3. Bind Timestamp & Today's Milk
            tvTimestamp.setText("Updated: " + record.getFormattedDateTime());
            if (todayMilk != null && todayMilk > 0) {
                tvCardMilkSummary.setVisibility(View.VISIBLE);
                tvCardMilkSummary.setText(String.format(Locale.US, "🥛 Today: %.1f L", todayMilk));
            } else if ("Milking".equalsIgnoreCase(profile.getLactationStatus())) {
                tvCardMilkSummary.setVisibility(View.VISIBLE);
                tvCardMilkSummary.setText("🥛 Tap to log today's milk");
            } else {
                tvCardMilkSummary.setVisibility(View.GONE);
            }

            // 4. Semantic Triage Color & Plain-Language Summary
            int greenColor = ContextCompat.getColor(ctx, R.color.status_healthy);
            int amberColor = ContextCompat.getColor(ctx, R.color.status_warning);
            int redColor = ContextCompat.getColor(ctx, R.color.status_anomaly);
            int defaultTextColor = ContextCompat.getColor(ctx, R.color.text_primary);
            int navyColor = ContextCompat.getColor(ctx, R.color.primary_navy);

            String cowName = profile.getCustomName();
            tvDiagnosticSummary.setText(record.getPlainLanguageSummary(cowName));

            // Health-to-Yield Insight Banner (Automatically shown on Fever!)
            if (record.isFever()) {
                tvYieldInsightBanner.setVisibility(View.VISIBLE);
                tvYieldInsightBanner.setText(R.string.yield_fever_warning);
                tvYieldInsightBanner.setTextColor(redColor);
            } else if (record.isCalvingDrop()) {
                tvYieldInsightBanner.setVisibility(View.VISIBLE);
                tvYieldInsightBanner.setText(R.string.yield_calving_warning);
                tvYieldInsightBanner.setTextColor(amberColor);
            } else {
                tvYieldInsightBanner.setVisibility(View.GONE);
            }

            if (record.isPredatorOrPanic()) {
                // HATARI: Crimson Red (#DC2626) - Predator / Panic
                viewStatusAccent.setBackgroundColor(redColor);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
                tvStatusBadge.setTextColor(redColor);
                tvStatusBadge.setText("🚨 DANGER • HATARI");

                layoutPlainSummaryBox.setBackgroundResource(R.drawable.bg_summary_danger);
                tvDiagnosticSummary.setTextColor(redColor);

                tvAccMagnitude.setTextColor(redColor);
                imgMotionIcon.setImageTintList(ColorStateList.valueOf(redColor));
                tvDistance.setTextColor(defaultTextColor);
                imgDistanceIcon.setImageTintList(ColorStateList.valueOf(navyColor));

            } else if (record.isStray()) {
                // HATARI: Crimson Red (#DC2626) - Strayed > 500m
                viewStatusAccent.setBackgroundColor(redColor);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
                tvStatusBadge.setTextColor(redColor);
                tvStatusBadge.setText("📍 STRAYING");

                layoutPlainSummaryBox.setBackgroundResource(R.drawable.bg_summary_danger);
                tvDiagnosticSummary.setTextColor(redColor);

                tvDistance.setTextColor(redColor);
                imgDistanceIcon.setImageTintList(ColorStateList.valueOf(redColor));
                tvAccMagnitude.setTextColor(defaultTextColor);
                imgMotionIcon.setImageTintList(ColorStateList.valueOf(navyColor));

            } else if (record.isFever()) {
                // HOMA: Warm Amber (#D97706) / Crimson Red for High Fever
                viewStatusAccent.setBackgroundColor(amberColor);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_warning);
                tvStatusBadge.setTextColor(amberColor);
                tvStatusBadge.setText("🌡️ FEVER ALERT");

                layoutPlainSummaryBox.setBackgroundResource(R.drawable.bg_summary_warning);
                tvDiagnosticSummary.setTextColor(amberColor);

                tvDistance.setTextColor(defaultTextColor);
                imgDistanceIcon.setImageTintList(ColorStateList.valueOf(navyColor));
                tvAccMagnitude.setTextColor(defaultTextColor);
                imgMotionIcon.setImageTintList(ColorStateList.valueOf(navyColor));

            } else if (record.isCalvingDrop()) {
                // KUZAA: Warm Amber (#D97706) - Calving Soon
                viewStatusAccent.setBackgroundColor(amberColor);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_warning);
                tvStatusBadge.setTextColor(amberColor);
                tvStatusBadge.setText("🐄 CALVING SOON");

                layoutPlainSummaryBox.setBackgroundResource(R.drawable.bg_summary_warning);
                tvDiagnosticSummary.setTextColor(amberColor);

                tvDistance.setTextColor(defaultTextColor);
                imgDistanceIcon.setImageTintList(ColorStateList.valueOf(navyColor));
                tvAccMagnitude.setTextColor(defaultTextColor);
                imgMotionIcon.setImageTintList(ColorStateList.valueOf(navyColor));

            } else {
                // SALAMA: Deep Forest Green (#0D7A4F) - Healthy
                viewStatusAccent.setBackgroundColor(greenColor);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_healthy);
                tvStatusBadge.setTextColor(greenColor);
                tvStatusBadge.setText("✓ HEALTHY");

                layoutPlainSummaryBox.setBackgroundResource(R.drawable.bg_summary_healthy);
                tvDiagnosticSummary.setTextColor(greenColor);

                tvDistance.setTextColor(defaultTextColor);
                imgDistanceIcon.setImageTintList(ColorStateList.valueOf(navyColor));
                tvAccMagnitude.setTextColor(defaultTextColor);
                imgMotionIcon.setImageTintList(ColorStateList.valueOf(navyColor));
            }

            // Temperature pill tinting
            if (record.isFever()) {
                tvTemperature.setTextColor(redColor);
                imgTempIcon.setImageTintList(ColorStateList.valueOf(redColor));
            } else if (record.isCalvingDrop()) {
                tvTemperature.setTextColor(amberColor);
                imgTempIcon.setImageTintList(ColorStateList.valueOf(amberColor));
            } else {
                tvTemperature.setTextColor(defaultTextColor);
                imgTempIcon.setImageTintList(ColorStateList.valueOf(greenColor));
            }

            // 5. Action Listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNodeClick(record);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onNodeLongClick(record, profile);
                    return true;
                }
                return false;
            });

            btnCardViewHealth.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNodeClick(record);
                }
            });

            btnCardLogMilk.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLogMilkClick(record, profile);
                }
            });
        }
    }
}
