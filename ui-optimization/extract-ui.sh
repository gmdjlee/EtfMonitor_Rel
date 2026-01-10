#!/bin/bash
# UI 코드 추출 스크립트

OUTPUT_DIR="/home/user/EtfMonitor_Rel/ui-optimization"
mkdir -p "$OUTPUT_DIR"

# 1. 파일별 통합 (섹션 구분)
echo "# EtfMonitor UI Code Extraction" > "$OUTPUT_DIR/all-ui-code.kt"
echo "# Generated: $(date)" >> "$OUTPUT_DIR/all-ui-code.kt"
echo "" >> "$OUTPUT_DIR/all-ui-code.kt"

# Core UI Components
echo "// ====== CORE UI COMPONENTS ======" >> "$OUTPUT_DIR/all-ui-code.kt"
for file in $(find /home/user/EtfMonitor_Rel/app/src/main/java/com/etfmonitor/core/ui -name "*.kt" | sort); do
    echo "" >> "$OUTPUT_DIR/all-ui-code.kt"
    echo "// FILE: ${file#/home/user/EtfMonitor_Rel/}" >> "$OUTPUT_DIR/all-ui-code.kt"
    cat "$file" >> "$OUTPUT_DIR/all-ui-code.kt"
done

# Feature Presentation (ViewModels 제외)
echo "" >> "$OUTPUT_DIR/all-ui-code.kt"
echo "// ====== FEATURE SCREENS ======" >> "$OUTPUT_DIR/all-ui-code.kt"
for file in $(find /home/user/EtfMonitor_Rel/app/src/main/java/com/etfmonitor/feature -path "*/presentation/*" -name "*Screen*.kt" | sort); do
    echo "" >> "$OUTPUT_DIR/all-ui-code.kt"
    echo "// FILE: ${file#/home/user/EtfMonitor_Rel/}" >> "$OUTPUT_DIR/all-ui-code.kt"
    cat "$file" >> "$OUTPUT_DIR/all-ui-code.kt"
done

# Components
echo "" >> "$OUTPUT_DIR/all-ui-code.kt"
echo "// ====== UI COMPONENTS ======" >> "$OUTPUT_DIR/all-ui-code.kt"
for file in $(find /home/user/EtfMonitor_Rel/app/src/main/java/com/etfmonitor/feature -path "*/component/*" -name "*.kt" | sort); do
    echo "" >> "$OUTPUT_DIR/all-ui-code.kt"
    echo "// FILE: ${file#/home/user/EtfMonitor_Rel/}" >> "$OUTPUT_DIR/all-ui-code.kt"
    cat "$file" >> "$OUTPUT_DIR/all-ui-code.kt"
done

wc -l "$OUTPUT_DIR/all-ui-code.kt"
