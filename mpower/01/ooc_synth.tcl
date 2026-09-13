# -----------------------------------------------------------------------------
# OOC synthesis
# -----------------------------------------------------------------------------

puts "INFO: Running OOC synthesis"
puts "INFO: Top:  $TOP"
puts "INFO: Part: $PART"

synth_design \
    -top $TOP \
    -part $PART \
    -mode out_of_context


# -----------------------------------------------------------------------------
# Reports
# -----------------------------------------------------------------------------

file mkdir $BUILD_DIR

report_utilization \
    -file [file join $BUILD_DIR utilization_synth.rpt]

report_timing_summary \
    -file [file join $BUILD_DIR timing_synth.rpt]


# -----------------------------------------------------------------------------
# Write checkpoint
# -----------------------------------------------------------------------------

set DCP_FILE [file join $BUILD_DIR "${TOP}.dcp"]

puts "INFO: Writing checkpoint: $DCP_FILE"

write_checkpoint -force $DCP_FILE

puts "INFO: OOC synthesis complete"