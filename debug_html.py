#!/usr/bin/env python3
import requests
from bs4 import BeautifulSoup
import time

def debug_statiz_page():
    url = "https://statiz.sporki.com/schedule/?m=daily&date=2024-07-01"
    
    try:
        # 사용자 에이전트 설정
        headers = {
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36'
        }
        
        response = requests.get(url, headers=headers)
        response.raise_for_status()
        
        soup = BeautifulSoup(response.content, 'html.parser')
        
        print("=== HTML 구조 분석 ===")
        print(f"페이지 제목: {soup.title.string if soup.title else 'None'}")
        
        # 경기 박스 찾기
        game_boxes = soup.select(".box_type_boared .item_box")
        print(f"\n경기 박스 개수: {len(game_boxes)}")
        
        for i, box in enumerate(game_boxes[:2]):  # 처음 2개만 분석
            print(f"\n--- 경기 {i+1} ---")
            
            # box_head 정보
            box_head = box.select_one(".box_head")
            if box_head:
                print(f"box_head: {box_head.get_text().strip()}")
            
            # 모든 링크 찾기
            links = box.select("a[href]")
            print(f"링크 개수: {len(links)}")
            
            for link in links:
                href = link.get('href', '')
                text = link.get_text().strip()
                if 's_no=' in href:
                    print(f"  ★ s_no 링크: {href} (텍스트: {text})")
                else:
                    print(f"  일반 링크: {href} (텍스트: {text})")
            
            # btn_box 확인
            btn_boxes = box.select("div.btn_box")
            print(f"btn_box 개수: {len(btn_boxes)}")
            for btn_box in btn_boxes:
                print(f"  btn_box HTML: {str(btn_box)[:200]}...")
        
        # 전체 페이지에서 s_no 링크 찾기
        all_s_no_links = soup.select("a[href*='s_no=']")
        print(f"\n전체 페이지 s_no 링크: {len(all_s_no_links)}개")
        for link in all_s_no_links[:5]:  # 처음 5개만
            print(f"  {link.get('href')} - {link.get_text().strip()}")
            
    except Exception as e:
        print(f"오류 발생: {e}")

if __name__ == "__main__":
    debug_statiz_page()
