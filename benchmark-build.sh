#!/bin/bash

# 构建性能测试脚本

echo "=========================================="
echo "   KocketMQ 构建性能基准测试"
echo "=========================================="

# 清理之前的构建
echo -e "\n🧹 清理之前的构建..."
./gradlew clean --no-daemon > /dev/null 2>&1
sleep 2

# 测试1: 冷启动构建（无缓存）
echo -e "\n📊 测试1: 冷启动构建（无Daemon，无缓存）"
echo "开始时间: $(date '+%H:%M:%S')"
START=$(date +%s%N)

./gradlew clean build test --no-daemon --no-build-cache 2>&1 | tail -20

END=$(date +%s%N)
COLD_TIME=$(( (END - START) / 1000000000 ))
echo "结束时间: $(date '+%H:%M:%S')"
echo "⏱️  冷启动耗时: ${COLD_TIME}秒"

# 清理
./gradlew clean --no-daemon > /dev/null 2>&1
sleep 2

# 测试2: Daemon + 缓存构建
echo -e "\n📊 测试2: 优化构建（Daemon + 增量编译 + 缓存）"
echo "开始时间: $(date '+%H:%M:%S')"
START=$(date +%s%N)

./gradlew clean build test 2>&1 | tail -20

END=$(date +%s%N)
OPTIMIZED_TIME=$(( (END - START) / 1000000000 ))
echo "结束时间: $(date '+%H:%M:%S')"
echo "⏱️  优化构建耗时: ${OPTIMIZED_TIME}秒"

# 测试3: 增量构建（无clean）
echo -e "\n📊 测试3: 增量构建（修改后重新编译）"
echo "开始时间: $(date '+%H:%M:%S')"
START=$(date +%s%N)

# 触摸一个文件模拟修改
touch broker/src/main/kotlin/com/agmtopy/kocketmq/broker/BrokerController.kt

./gradlew build test 2>&1 | tail -20

END=$(date +%s%N)
INCREMENTAL_TIME=$(( (END - START) / 1000000000 ))
echo "结束时间: $(date '+%H:%M:%S')"
echo "⏱️  增量构建耗时: ${INCREMENTAL_TIME}秒"

# 显示性能信息
echo -e "\n=========================================="
echo "   构建性能对比结果"
echo "=========================================="
echo "冷启动构建:      ${COLD_TIME}秒"
echo "优化构建:        ${OPTIMIZED_TIME}秒"
echo "增量构建:        ${INCREMENTAL_TIME}秒"
echo "=========================================="

# 计算优化效果
if [ $COLD_TIME -gt 0 ]; then
    IMPROVEMENT=$(( (COLD_TIME - OPTIMIZED_TIME) * 100 / COLD_TIME ))
    echo "🚀 性能提升: ${IMPROVEMENT}%"
fi

# 显示Gradle配置
echo -e "\n=========================================="
echo "   当前Gradle配置"
echo "=========================================="
./gradlew buildScanInfo --quiet

echo -e "\n✅ 测试完成！"
