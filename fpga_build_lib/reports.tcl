# ============================================================
# reports.tcl
# Reusable reporting helpers.
#
# Supported BUILD_MODE values:
#   dry_run
#   vivado
#
# Future:
#   quartus
# ============================================================


proc write_utilization_report {path} {
    set path [file normalize $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: write utilization report: $path"
        return
    }

    if {[is_vivado]} {
        report_utilization \
            -file $path

        return
    }

    error "write_utilization_report not implemented for BUILD_MODE"
}


proc write_timing_summary_report {path} {
    set path [file normalize $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: write timing summary report: $path"
        return
    }

    if {[is_vivado]} {
        report_timing_summary \
            -file $path

        return
    }

    error "write_timing_summary_report not implemented for BUILD_MODE"
}