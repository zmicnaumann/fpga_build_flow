# ============================================================
# synth_ooc.tcl
# ============================================================

run_ooc_synthesis $TOP $PART


write_dcp \
    "$BUILD_DIR/${TOP}_synth.dcp"


write_utilization_report \
    "$REPORT_DIR/${TOP}_utilization.rpt"


write_timing_report \
    "$REPORT_DIR/${TOP}_timing_summary.rpt"