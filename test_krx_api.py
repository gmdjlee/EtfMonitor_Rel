#!/usr/bin/env python3
"""
KRX API 기능 테스트 스크립트

feargreed.py의 KRX API 호출을 테스트합니다.
- Option data (Call/Put)
- Market indices (KOSPI/KOSDAQ)
- Derivative indices (5년국채, 10년국채, VKOSPI)
- Fear & Greed Index 계산
"""

import sys
import os
from datetime import datetime, timedelta

# Add app/src/main/python to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'app', 'src', 'main', 'python'))

def test_feargreed_module():
    """Test feargreed.py module functionality"""
    print("=" * 80)
    print("TEST 1: Fear & Greed Index Module")
    print("=" * 80)

    try:
        import feargreed
        print("✅ Module import successful")
    except ImportError as e:
        print(f"❌ Module import failed: {e}")
        return False

    # Test date range (last 30 days)
    end_date = datetime.now()
    start_date = end_date - timedelta(days=30)
    start_str = start_date.strftime("%Y%m%d")
    end_str = end_date.strftime("%Y%m%d")

    print(f"\nTest period: {start_str} ~ {end_str}")

    # Test 1.1: KRXFetcher initialization
    print("\n--- Test 1.1: KRXFetcher Initialization ---")
    try:
        fetcher = feargreed.KRXFetcher()
        print("✅ KRXFetcher initialized successfully")
    except Exception as e:
        print(f"❌ KRXFetcher initialization failed: {e}")
        return False

    # Test 1.2: Option data (Call)
    print("\n--- Test 1.2: Call Option Data ---")
    try:
        call_df = fetcher.get_option(start_str, end_str, "C")
        if call_df is not None and not call_df.empty:
            print(f"✅ Call option data retrieved: {len(call_df)} records")
            print(f"   Columns: {list(call_df.columns)}")
            print(f"   Sample data:\n{call_df.head(3)}")
        else:
            print("⚠️  Call option data is empty")
    except Exception as e:
        print(f"❌ Call option data retrieval failed: {e}")

    # Test 1.3: Option data (Put)
    print("\n--- Test 1.3: Put Option Data ---")
    try:
        put_df = fetcher.get_option(start_str, end_str, "P")
        if put_df is not None and not put_df.empty:
            print(f"✅ Put option data retrieved: {len(put_df)} records")
            print(f"   Columns: {list(put_df.columns)}")
            print(f"   Sample data:\n{put_df.head(3)}")
        else:
            print("⚠️  Put option data is empty")
    except Exception as e:
        print(f"❌ Put option data retrieval failed: {e}")

    # Test 1.4: Market indices (KOSPI)
    print("\n--- Test 1.4: KOSPI Index Data ---")
    try:
        kospi_df = fetcher.get_index(start_str, end_str, "KOSPI")
        if kospi_df is not None and not kospi_df.empty:
            print(f"✅ KOSPI data retrieved: {len(kospi_df)} records")
            print(f"   Columns: {list(kospi_df.columns)}")
            print(f"   Sample data:\n{kospi_df.head(3)}")
        else:
            print("⚠️  KOSPI data is empty")
    except Exception as e:
        print(f"❌ KOSPI data retrieval failed: {e}")

    # Test 1.5: Market indices (KOSDAQ)
    print("\n--- Test 1.5: KOSDAQ Index Data ---")
    try:
        kosdaq_df = fetcher.get_index(start_str, end_str, "KOSDAQ")
        if kosdaq_df is not None and not kosdaq_df.empty:
            print(f"✅ KOSDAQ data retrieved: {len(kosdaq_df)} records")
            print(f"   Columns: {list(kosdaq_df.columns)}")
            print(f"   Sample data:\n{kosdaq_df.head(3)}")
        else:
            print("⚠️  KOSDAQ data is empty")
    except Exception as e:
        print(f"❌ KOSDAQ data retrieval failed: {e}")

    # Test 1.6: Derivative indices (5-year bond)
    print("\n--- Test 1.6: 5-Year Bond Index Data ---")
    try:
        bond5y_df = fetcher.get_index(start_str, end_str, "5년국채")
        if bond5y_df is not None and not bond5y_df.empty:
            print(f"✅ 5-year bond data retrieved: {len(bond5y_df)} records")
            print(f"   Columns: {list(bond5y_df.columns)}")
            print(f"   Sample data:\n{bond5y_df.head(3)}")
        else:
            print("⚠️  5-year bond data is empty")
    except Exception as e:
        print(f"❌ 5-year bond data retrieval failed: {e}")

    # Test 1.7: Derivative indices (10-year bond)
    print("\n--- Test 1.7: 10-Year Bond Index Data ---")
    try:
        bond10y_df = fetcher.get_index(start_str, end_str, "10년국채")
        if bond10y_df is not None and not bond10y_df.empty:
            print(f"✅ 10-year bond data retrieved: {len(bond10y_df)} records")
            print(f"   Columns: {list(bond10y_df.columns)}")
            print(f"   Sample data:\n{bond10y_df.head(3)}")
        else:
            print("⚠️  10-year bond data is empty")
    except Exception as e:
        print(f"❌ 10-year bond data retrieval failed: {e}")

    # Test 1.8: Derivative indices (VKOSPI)
    print("\n--- Test 1.8: VKOSPI (Volatility Index) Data ---")
    try:
        vkospi_df = fetcher.get_index(start_str, end_str, "VKOSPI")
        if vkospi_df is not None and not vkospi_df.empty:
            print(f"✅ VKOSPI data retrieved: {len(vkospi_df)} records")
            print(f"   Columns: {list(vkospi_df.columns)}")
            print(f"   Sample data:\n{vkospi_df.head(3)}")
        else:
            print("⚠️  VKOSPI data is empty")
    except Exception as e:
        print(f"❌ VKOSPI data retrieval failed: {e}")

    # Test 1.9: Fear & Greed calculation
    print("\n--- Test 1.9: Fear & Greed Index Calculation ---")
    try:
        kospi_result, kosdaq_result = feargreed.run_analysis(start_str, end_str)

        if kospi_result is not None and not kospi_result.empty:
            print(f"✅ KOSPI Fear & Greed calculated: {len(kospi_result)} records")
            print(f"   Columns: {list(kospi_result.columns)}")
            print(f"   Sample data:\n{kospi_result[['거래일', 'KOSPI', 'FG', 'Osc']].head(3)}")
        else:
            print("⚠️  KOSPI Fear & Greed result is empty")

        if kosdaq_result is not None and not kosdaq_result.empty:
            print(f"✅ KOSDAQ Fear & Greed calculated: {len(kosdaq_result)} records")
            print(f"   Columns: {list(kosdaq_result.columns)}")
            print(f"   Sample data:\n{kosdaq_result[['거래일', 'KOSDAQ', 'FG', 'Osc']].head(3)}")
        else:
            print("⚠️  KOSDAQ Fear & Greed result is empty")

        # Check if any result was successful
        if (kospi_result is not None and not kospi_result.empty) or \
           (kosdaq_result is not None and not kosdaq_result.empty):
            print("\n✅ Fear & Greed calculation successful")
            return True
        else:
            print("\n❌ Fear & Greed calculation produced no results")
            return False

    except Exception as e:
        print(f"❌ Fear & Greed calculation failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """Run all KRX API tests"""
    print("\n" + "=" * 80)
    print("KRX API FUNCTIONALITY TEST")
    print("=" * 80)
    print(f"Test started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 80 + "\n")

    results = {
        'Fear & Greed Index': test_feargreed_module()
    }

    # Summary
    print("\n" + "=" * 80)
    print("TEST SUMMARY")
    print("=" * 80)

    total_tests = len(results)
    passed_tests = sum(1 for v in results.values() if v)
    failed_tests = total_tests - passed_tests

    for test_name, result in results.items():
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"{status} - {test_name}")

    print("\n" + "-" * 80)
    print(f"Total: {total_tests} | Passed: {passed_tests} | Failed: {failed_tests}")
    print(f"Success rate: {passed_tests/total_tests*100:.1f}%")
    print("=" * 80)

    return failed_tests == 0

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
