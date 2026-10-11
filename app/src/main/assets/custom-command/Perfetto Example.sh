echo 'Perfetto - https://ui.perfetto.dev/'

TIMESTAMP=$(date "+%Y-%m-%d_%H-%M-%S")
TRACE_FILE="/data/local/traces/${TIMESTAMP}.perfetto-trace"

cat <<EOF | perfetto --txt -c - -o "$TRACE_FILE"
buffers {
  size_kb: 65536
  fill_policy: DISCARD
}
buffers {
  size_kb: 4096
  fill_policy: DISCARD
}
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched/sched_process_exit"
      ftrace_events: "sched/sched_process_free"
      ftrace_events: "task/task_newtask"
      ftrace_events: "task/task_rename"
      ftrace_events: "sched/sched_switch"
      ftrace_events: "power/suspend_resume"
      ftrace_events: "sched/sched_blocked_reason"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "sched/sched_wakeup_new"
      ftrace_events: "sched/sched_waking"
      ftrace_events: "sched/sched_process_exit"
      ftrace_events: "sched/sched_process_free"
      ftrace_events: "task/task_newtask"
      ftrace_events: "task/task_rename"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      ftrace_events: "power/suspend_resume"
      ftrace_events: "power/psci_domain_idle_enter"
      ftrace_events: "power/psci_domain_idle_exit"
      ftrace_events: "power/gpu_frequency"
      symbolize_ksyms: true
      disable_generic_events: true
    }
  }
}
data_sources {
  config {
    name: "linux.process_stats"
    target_buffer: 1
    process_stats_config {
      scan_all_processes_on_start: true
    }
  }
}
data_sources {
  config {
    name: "linux.sys_stats"
    sys_stats_config {
      stat_period_ms: 1000
      stat_counters: STAT_CPU_TIMES
      stat_counters: STAT_FORK_COUNT
      cpufreq_period_ms: 1000
    }
  }
}
data_sources {
  config {
    name: "android.power"
    android_power_config {
      battery_poll_ms: 250
      battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
      battery_counters: BATTERY_COUNTER_CHARGE
      battery_counters: BATTERY_COUNTER_CURRENT
      collect_power_rails: true
    }
  }
}
data_sources {
  config {
    name: "android.surfaceflinger.frametimeline"
  }
}
EOF
