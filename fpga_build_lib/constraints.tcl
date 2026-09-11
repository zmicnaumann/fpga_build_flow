# ============================================================
# constraints.tcl
# Reusable constraint-loading helpers.
#
# Supported BUILD_MODE values:
#   dry_run
#   vivado
#
# Future:
#   quartus
# ============================================================


proc add_xdc {path} {
    set path [require_file $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: add XDC: $path"
        return
    }

    if {[is_vivado]} {
        read_xdc $path
        return
    }

    error "add_xdc not implemented for BUILD_MODE"
}


proc add_ooc_xdc {path} {
    set path [require_file $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: add OOC XDC: $path"
        return
    }

    if {[is_vivado]} {
        read_xdc \
            -mode out_of_context \
            $path
        return
    }

    error "add_ooc_xdc not implemented for BUILD_MODE"
}