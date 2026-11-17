document.addEventListener('DOMContentLoaded', () => {
  if (document.body.dataset.liveRefresh !== 'true') return;
  window.setTimeout(() => {
    if (!document.hidden && !document.querySelector(':focus-visible')) window.location.reload();
  }, 15_000);
});
