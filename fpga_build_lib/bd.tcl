# ============================================================
# bd.tcl
# Reusable generated-design helpers.
#
# Supported BUILD_MODE values:
#   dry_run
#   vivado
#
# Future:
#   quartus
# ============================================================


proc generate_bd {bd_tcl} {
    set bd_tcl [require_file $bd_tcl]

    if {[is_dry_run]} {
        log_info "DRY RUN: source BD Tcl: $bd_tcl"
        return
    }

    if {[is_vivado]} {
        source $bd_tcl

        validate_bd_design
        save_bd_design
        return
    }

    error "generate_bd not implemented for BUILD_MODE"
}


proc generate_bd_wrapper {bd_name} {

    if {[is_dry_run]} {
        log_info "DRY RUN: generate wrapper for BD: $bd_name"
        return
    }

    if {[is_vivado]} {

        set bd_file [get_files "${bd_name}.bd"]

        if {[llength $bd_file] == 0} {
            error "Could not find generated BD: ${bd_name}.bd"
        }

        make_wrapper \
            -files $bd_file \
            -top

        return
    }

    error "generate_bd_wrapper not implemented for BUILD_MODE"
}