# ============================================================
# synthesis.tcl
# Reusable synthesis helpers.
#
# Supported BUILD_MODE values:
#   dry_run
#   vivado
#
# Future:
#   quartus
# ============================================================


proc synthesize_ooc {top part} {

    if {[is_dry_run]} {
        log_info "DRY RUN: OOC synthesis"
        log_info "DRY RUN:   top  = $top"
        log_info "DRY RUN:   part = $part"
        return
    }

    if {[is_vivado]} {
        synth_design \
            -top $top \
            -part $part \
            -mode out_of_context

        return
    }

    error "synthesize_ooc not implemented for BUILD_MODE"
}


proc write_synthesis_dcp {path} {
    set path [file normalize $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: write synthesis checkpoint: $path"
        return
    }

    if {[is_vivado]} {
        write_checkpoint \
            -force \
            $path

        return
    }

    error "write_synthesis_dcp not implemented for BUILD_MODE"
}