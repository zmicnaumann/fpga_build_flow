# ============================================================
# sources.tcl
# Reusable source-loading helpers for FPGA build flows.
#
# Supported BUILD_MODE values:
#   dry_run
#   vivado
#
# Future:
#   quartus
# ============================================================


proc add_systemverilog {path} {
    set path [require_file $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: add SystemVerilog: $path"
        return
    }

    if {[is_vivado]} {
        read_verilog -sv $path
        return
    }

    error "add_systemverilog not implemented for BUILD_MODE"
}


proc add_verilog {path} {
    set path [require_file $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: add Verilog: $path"
        return
    }

    if {[is_vivado]} {
        read_verilog $path
        return
    }

    error "add_verilog not implemented for BUILD_MODE"
}


proc add_vhdl {path} {
    set path [require_file $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: add VHDL: $path"
        return
    }

    if {[is_vivado]} {
        read_vhdl $path
        return
    }

    error "add_vhdl not implemented for BUILD_MODE"
}


proc add_ip {path} {
    set path [require_file $path]

    if {[is_dry_run]} {
        log_info "DRY RUN: add IP: $path"
        return
    }

    if {[is_vivado]} {
        read_ip $path
        return
    }

    error "add_ip not implemented for BUILD_MODE"
}


proc add_bd {path} {
    set path [file normalize $path]

    # Generated BD may not exist yet during a dry run.
    if {[is_dry_run]} {
        log_info "DRY RUN: expecting generated BD: $path"
        log_info "DRY RUN: add BD: $path"
        return
    }

    if {[is_vivado]} {
        set path [require_file $path]
        read_bd $path
        return
    }

    error "add_bd not implemented for BUILD_MODE"
}


proc add_generated_verilog {path} {
    set path [file normalize $path]

    # Generated wrapper may not exist yet during a dry run.
    if {[is_dry_run]} {
        log_info "DRY RUN: expecting generated Verilog: $path"
        log_info "DRY RUN: add generated Verilog: $path"
        return
    }

    if {[is_vivado]} {
        set path [require_file $path]
        read_verilog $path
        return
    }

    error "add_generated_verilog not implemented for BUILD_MODE"
}