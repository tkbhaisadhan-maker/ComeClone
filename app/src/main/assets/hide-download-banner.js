(() => {
  const host = "__TARGET_HOST__";
  const key = "app-banner-hide";
  const value = "1";

  if (location.hostname && location.hostname.toLowerCase() !== host) {
    return "skipped-host";
  }

  const writeFlag = () => {
    try {
      window.sessionStorage.setItem(key, value);
      return window.sessionStorage.getItem(key) === value;
    } catch (error) {
      return false;
    }
  };

  // Site ka apna "first visit" flag pin karte hain taaki top download bar na dikhe.
  writeFlag();

  try {
    const proto = Storage.prototype;
    const originalGetItem = proto.getItem;
    const originalSetItem = proto.setItem;
    const originalRemoveItem = proto.removeItem;
    const originalClear = proto.clear;

    const isPinnedKey = (storage, name) =>
      storage === window.sessionStorage && String(name) === key;

    proto.getItem = function (name) {
      if (isPinnedKey(this, name)) return value;
      return originalGetItem.call(this, name);
    };
    proto.setItem = function (name, nextValue) {
      if (isPinnedKey(this, name)) return originalSetItem.call(this, key, value);
      return originalSetItem.call(this, name, nextValue);
    };
    proto.removeItem = function (name) {
      if (isPinnedKey(this, name)) {
        originalSetItem.call(this, key, value);
        return;
      }
      return originalRemoveItem.call(this, name);
    };
    proto.clear = function () {
      const result = originalClear.call(this);
      if (this === window.sessionStorage) {
        try { originalSetItem.call(this, key, value); } catch (error) {}
      }
      return result;
    };
  } catch (error) {
    // Hooks optional hain; upar wala setItem normal WebView me kaafi hai.
  }

  return writeFlag() ? "hidden" : "failed";
})();
