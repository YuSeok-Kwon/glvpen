/**
 * AI Insight 모듈 — 차트 데이터 기반 Gemini AI 설명 자동 생성
 * 요청 큐: 한 번에 1개씩 순차 처리, 요청 간 2초 딜레이 (API 할당량 보호)
 */
const AiInsight = (() => {
  const queue = [];
  let processing = false;
  const DELAY_MS = 2000;

  function requestInsight(params) {
    queue.push(params);
    showLoading(params.targetDivId);
    if (!processing) processQueue();
  }

  async function processQueue() {
    processing = true;
    while (queue.length > 0) {
      const params = queue.shift();
      await sendRequest(params);
      if (queue.length > 0) {
        await sleep(DELAY_MS);
      }
    }
    processing = false;
  }

  async function sendRequest(params) {
    const { chartType, title, labels, datasets, extra, targetDivId } = params;
    const targetDiv = document.getElementById(targetDivId);
    if (!targetDiv) return;

    try {
      const body = { chartType, title, labels, datasets, extra };
      const res = await fetch('/api/analysis/ai-insight', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });

      if (!res.ok) throw new Error('API 응답 오류');

      const data = await res.json();

      if (data.success && data.insight) {
        renderSuccess(targetDiv, data.insight);
      } else {
        renderError(targetDiv, data.errorMessage || 'AI 분석을 수행할 수 없습니다.');
      }
    } catch (e) {
      renderError(targetDiv, 'AI 분석 요청에 실패했습니다.');
    }
  }

  function showLoading(targetDivId) {
    const div = document.getElementById(targetDivId);
    if (!div) return;
    div.innerHTML =
      '<div class="ai-insight-area">' +
        '<div class="ai-insight-loading">' +
          '<div class="spinner-border text-success" role="status"></div>' +
          '<span>AI 분석 중...</span>' +
        '</div>' +
      '</div>';
  }

  function renderSuccess(div, insight) {
    div.innerHTML =
      '<div class="ai-insight-area">' +
        '<div class="ai-insight-content">' +
          '<div class="ai-insight-header"><i class="bi bi-robot"></i> AI 분석</div>' +
          '<div class="ai-insight-body">' + insight + '</div>' +
        '</div>' +
      '</div>';
  }

  function renderError(div, message) {
    div.innerHTML =
      '<div class="ai-insight-area">' +
        '<div class="ai-insight-error"><i class="bi bi-info-circle"></i> ' + message + '</div>' +
      '</div>';
  }

  function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  return { requestInsight };
})();
