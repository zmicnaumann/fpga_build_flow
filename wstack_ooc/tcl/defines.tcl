# ============================================================
# defines.tcl
#
# Wrapper functions around Vivado operations.
# Allows the flow to run under ordinary tclsh in dry-run mode.
# ============================================================


proc log_info {msg} {
    puts "INFO: $msg"
}


proc log_error {msg} {
    puts stderr "ERROR: $msg"
}


proc require_file {path} {

    set path [file normalize $path]

    if {![file exists $path]} {
        error "Required file does not exist: $path"
    }

    if {![file isfile $path]} {
        error "Expected file but found something else: $path"
    }

    log_info "Found file: $path"

    return $path
}


proc require_dir {path} {

    set path [file normalize $path]

    if {![file exists $path]} {
        error "Required directory does not exist: $path"
    }

    if {![file isdirectory $path]} {
        error "Expected directory: $path"
    }

    return $path
}


proc ensure_dir {path} {

    if {![file exists $path]} {
        file mkdir $path
        log_info "Created directory: $path"
    }

    return [file normalize $path]
}

proc add_verilog {path} {

    global BUILD_MODE

    set path [require_file $path]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: read_verilog $path"
        return
    }

    read_verilog $path
}

proc add_systemverilog {path} {

    global BUILD_MODE

    set path [require_file $path]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: read_verilog -sv $path"
        return
    }

    read_verilog -sv $path
}

proc add_vhdl {path} {

    global BUILD_MODE

    set path [require_file $path]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: read_vhdl $path"
        return
    }

    read_vhdl $path
}

proc add_ip {path} {

    global BUILD_MODE

    set path [require_file $path]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: read_ip $path"
        return
    }

    read_ip $path
}

proc generate_bd {bd_tcl} {
    global BUILD_MODE

    set bd_tcl [require_file $bd_tcl]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: source $bd_tcl"
        return
    }

    source $bd_tcl

    validate_bd_design
    save_bd_design
}

proc generate_bd_wrapper {bd_name} {
    global BUILD_MODE

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: make_wrapper for $bd_name"
        return
    }

    set bd_file [get_files "${bd_name}.bd"]

    if {[llength $bd_file] == 0} {
        error "Could not find BD: ${bd_name}.bd"
    }

    make_wrapper \
        -files $bd_file \
        -top
}

proc add_bd {path} {
    global BUILD_MODE

    set path [file normalize $path]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: expecting generated BD: $path"
        log_info "DRY RUN: read_bd $path"
        return
    }

    set path [require_file $path]
    read_bd $path
}

proc add_generated_verilog {path} {
    global BUILD_MODE

    set path [file normalize $path]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: expecting generated Verilog: $path"
        log_info "DRY RUN: read_verilog $path"
        return
    }

    set path [require_file $path]
    read_verilog $path
}

proc add_ooc_xdc {path} {

    global BUILD_MODE

    set path [require_file $path]

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: read_xdc -mode out_of_context $path"
        return
    }

    read_xdc -mode out_of_context $path
}

proc run_ooc_synthesis {top part} {

    global BUILD_MODE

    log_info "Synthesis configuration:"
    log_info "  TOP  = $top"
    log_info "  PART = $part"

    if {$BUILD_MODE eq "dry"} {

        log_info "DRY RUN:"
        log_info "  synth_design -top $top -part $part -mode out_of_context"

        return
    }

    synth_design \
        -top $top \
        -part $part \
        -mode out_of_context
}

proc write_dcp {path} {

    global BUILD_MODE

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: write_checkpoint -force $path"
        return
    }

    write_checkpoint -force $path
}

proc write_utilization_report {path} {

    global BUILD_MODE

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: report_utilization -file $path"
        return
    }

    report_utilization -file $path
}


proc write_timing_report {path} {

    global BUILD_MODE

    if {$BUILD_MODE eq "dry"} {
        log_info "DRY RUN: report_timing_summary -file $path"
        return
    }

    report_timing_summary -file $path
}