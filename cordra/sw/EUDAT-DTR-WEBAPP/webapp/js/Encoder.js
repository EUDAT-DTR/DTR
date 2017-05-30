/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;
window.cnri = window.cnri || {};
cnri.util = cnri.util || {};

var Encoder = cnri.util.Encoder = {};

Encoder.Utf16 = {};

Encoder.Utf16.bytes = function(s) {
    var arr = new Uint8Array(s.length*2);
    var pos = 0;
    for (var i=0; i < s.length; i++) {
        var code = s.charCodeAt(i);
        arr[pos++] = code >>> 8;
        arr[pos++] = code & 0xFF;
    }
    return arr;
};

Encoder.Utf16.calcNumBytes = function(s) {
    return s.length * 2;
};

Encoder.Utf16.string = function(arr) {
    var res = '';
    for(var i = 0; i+1 < arr.length; i+=2) {
        var code = (arr[i] << 8) | arr[i+1];
        res += String.fromCharCode(code);
    }
    if(i < arr.length) res += String.fromCharCode(0xFFFD);
    return res;
};

Encoder.Utf8 = {};

Encoder.Utf8.bytes = function(s) {
    var res = new Uint8Array(Encoder.Utf8.calcNumBytes(s));
    var pos = 0;
    for(var i = 0; i < s.length; i++) {
        var code = s.charCodeAt(i);
        if(code <= 0x7F) {
            res[pos++] = code;
        } else if(code <= 0x7FF) {
            res[pos++] = 0xC0 | (code >>> 6);
            res[pos++] = 0x80 | (code & 0x3F);
        } else if(0xD800 <= code && code <= 0xDBFF) {
            if(i+1 < s.length) {
                var next = s.charCodeAt(i+1);
                if(0xDC00 <= next && next <= 0xDFFF) {
                    i++;
                    code = (((code - 0xD800) * 0x400) | (next - 0xDC00)) + 0x10000;
                    res[pos++] = 0xF0 | (code >>> 18);
                    res[pos++] = 0x80 | ((code >>> 12) & 0x3F);
                    res[pos++] = 0x80 | ((code >>> 6) & 0x3F);
                    res[pos++] = 0x80 | (code & 0x3F);
                    continue;
                }
            }
            // bare surrogate
            res[pos++] = 0xE0 | (code >>> 12);
            res[pos++] = 0x80 | ((code >>> 6) & 0x3F);
            res[pos++] = 0x80 | (code & 0x3F);
        }
        else {
            res[pos++] = 0xE0 | (code >>> 12);
            res[pos++] = 0x80 | ((code >>> 6) & 0x3F);
            res[pos++] = 0x80 | (code & 0x3F);
        }
    }
    return res;
};

Encoder.Utf8.calcNumBytes = function(s) {
    var res = 0;
    for(var i = 0; i < s.length; i++) {
        var code = s.charCodeAt(i);
        if(code <= 0x7F) res += 1;
        else if(code <= 0x7FF) res += 2;
        else if(0xD800 <= code && code <= 0xDBFF) {
            if(i+1 < s.length) {
                var next = s.charCodeAt(i+1);
                if(0xDC00 <= next && next <= 0xDFFF) {
                    i++;
                    res += 4;
                    continue;
                }
            }
            // bare surrogate
            res += 3;
        }
        else res += 3;
    }
    return res;
};

Encoder.Utf8.string = function(arr) {
    var str = '';
    for(var i = 0; i < arr.length; i++) {
        var code = arr[i];
        var thisValid = false;
        if(code <= 0x7F) {
            thisValid = true;
//            if(!binary && (code <= 0x08 || (0x0E <= code && code < 0x20) || code===0x7F)) binary = true;
            str += String.fromCharCode(code);
        } else if(code <= 0xC1 || code >= 0xF5) {
            thisValid = false;
        } else if(code <= 0xDF) {
            if(i+1<arr.length) {
                var c2 = arr[i+1];
                if(0x80 <= c2 && c2 <= 0xBF) {
                    thisValid = true;
                    i++;
                    str += String.fromCharCode(((code & 0x1F) << 6) | (c2 & 0x3F));
                }
            }
        } else if(code <= 0xEF) {
            if(i+2 < arr.length) {
                var c2 = arr[i+1];
                var c3 = arr[i+2];
                if(0x80 <= c2 && c2 <= 0xBF && 0x80 <= c3 && c3 <= 0xBF && !(code===0xE0 && c2 <= 0x9F)) {
                    thisValid = true;
                    i+=2;
                    str += String.fromCharCode(((code & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
                }
            }
        } else {
            if(i+3 < arr.length) {
                var c2 = arr[i+1];
                var c3 = arr[i+2];
                var c4 = arr[i+3];
                if(0x80 <= c2 && c2 <= 0xBF && 0x80 <= c3 && c3 <= 0xBF && 0x80 <= c4 && c4 <= 0xBF && !(code===0xF0 && c2 <= 0x8F)) {
                    code = ((code & 0x07) << 18) | ((c2 & 0x3F) << 12) | ((c3 & 0x3F) << 6) | (c4 & 0x3F);
                    if(code <= 0x10FFFF) {
                        thisValid = true;
                        i+=3;
                        code -= 0x10000;
                        str += String.fromCharCode(0xD800 + (code >> 10), 0xDC00 + (code & 0x3FF));
                    }
                }
            }
        }
        if(!thisValid) {
            str += String.fromCharCode(0xFFFD);
        }
    }
    return str;
};

Encoder.Utf8.looksLikeBinary = function(arr) {
    for(var i = 0; i < arr.length; i++) {
        var code = arr[i];
        if(code <= 0x7F) {
            if(code <= 0x08 || (0x0E <= code && code < 0x20) || code===0x7F) return true;
        } else if(code <= 0xC1 || code >= 0xF5) {
            return true;
        } else if(code <= 0xDF) {
            if(i+1>=arr.length) return true;
            var c2 = arr[++i];
            if(!(0x80 <= c2 && c2 <= 0xBF)) return true;
        } else if(code <= 0xEF) {
            if(i+2 >= arr.length) return true;
            var c2 = arr[++i];
            var c3 = arr[++i];
            if(!(0x80 <= c2 && c2 <= 0xBF && 0x80 <= c3 && c3 <= 0xBF && !(code===0xE0 && c2 <= 0x9F))) return true;
        } else {
            if(i+3 >= arr.length) return true;
            var c2 = arr[++i];
            var c3 = arr[++i];
            var c4 = arr[++i];
            if(!(0x80 <= c2 && c2 <= 0xBF && 0x80 <= c3 && c3 <= 0xBF && 0x80 <= c4 && c4 <= 0xBF && !(code===0xF0 && c2 <= 0x8F))) return true;
        }
    }
    return false;
};

Encoder.Utf8.stringLooksLikeBinary = function(s) {
    for(var i = 0; i < s.length; i++) {
        var code = s.charCodeAt(i);
        if(code <= 0x08 || (0x0E <= code && code < 0x20) || code===0x7F || code===0xFFFD) return true;
    }
    return false;
};

Encoder.Base64 = {};

var un = undefined;

var base64DecodeArray = [
    un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, 
    un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, 
    un, un, un, un, un, un, un, un, un, un, un, 62, un, 62, un, 63, 
    52, 53, 54, 55, 56, 57, 58, 59, 60, 61, un, un, un, un, un, un, 
    un,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 
    15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, un, un, un, un, 63, 
    un, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 
    41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
];

var base64EncodeArray = [
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 
    'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 
    'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 
    'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
];

Encoder.Base64.bytes = function(s) {
    var res = new Uint8Array(Encoder.Base64.calcNumBytes(s));
    var pos = 0;
    var inFour = 0;
    var accum = 0;
    for(var i = 0; i < s.length; i++) {
        var code = base64DecodeArray[s.charCodeAt(i)];
        if(code===undefined) continue;
        if(inFour===0) {
            accum = code << 2;
        } else if(inFour===1) {
            accum |= code >>> 4;
            res[pos++] = accum;
            accum = (code & 0x0F) << 4;
        }  else if(inFour===2) {
            accum |= code >>> 2;
            res[pos++] = accum;
            accum = (code & 0x03) << 6;
        } else {
            accum |= code;
            res[pos++] = accum;
        }
        inFour = (inFour + 1) % 4;
    }
    return res.subarray(0,pos);
};
    
Encoder.Base64.calcNumBytes = function(s) {
    var len = s.length;
    if(s[s.length-1]==='=') {
        len -= 1;
        if(s[s.length-2]==='=') len -= 1;
    }
    var mod = len % 4;
    if(mod===0) return 3 * len / 4;
    if(mod===1) return 3 * (len - 1) / 4;
    if(mod===2) return 3 * (len - 2) / 4 + 1;
    return 3 * (len - 3) / 4 + 2;
};
  
function genericBase64EncoderFunction(base64EncodeArray, usePad) {
    return function(arr, chars) {
        var thisBase64EncodeArray = base64EncodeArray;
        if(chars && chars[0] && chars[1]) {
            thisBase64EncodeArray = base64EncodeArray.slice(0);
            thisBase64EncodeArray[62] = chars[0];
            thisBase64EncodeArray[63] = chars[1];
        }
        var s = '';
        var accum = 0;
        var inThree = 0;
        for(var i = 0; i < arr.length; i++) {
            var code = arr[i];
            if(inThree===0) {
                s += thisBase64EncodeArray[code >>> 2];
                accum = (code & 0x03) << 4;
            } else if(inThree===1) {
                accum |= code >>> 4;
                s += thisBase64EncodeArray[accum];
                accum = (code & 0x0F) << 2;
            } else {
                accum |= code >>> 6;
                s += thisBase64EncodeArray[accum];
                s += thisBase64EncodeArray[code & 0x3F];
            }
            inThree = (inThree + 1) % 3;
        }
        if(inThree > 0) {
            s += thisBase64EncodeArray[accum];
            if((!chars && usePad) || (chars && chars[2])) {
                var pad = '=';
                if(chars) pad = chars[2];
                s += pad;
                if(inThree===1) s += pad;
            } 
        }
        return s;
    };
}

Encoder.Base64.string = genericBase64EncoderFunction(base64EncodeArray, true);

function clone(obj) {
    var res = {};
    for(var key in obj) {
        if(obj.hasOwnProperty(key)) res[key] = obj[key];
    }
    return res;
}

var base64UrlEncodeArray = [
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 
    'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 
    'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 
    'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
];

Encoder.Base64Url = clone(Encoder.Base64);

Encoder.Base64Url.string = genericBase64EncoderFunction(base64UrlEncodeArray, false);

Encoder.Hex = {};

var hexDecodeArray = [
    un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, 
    un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, 
    un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, 
     0,  1,  2,  3,  4,  5,  6,  7,  8,  9, un, un, un, un, un, un, 
    un, 10, 11, 12, 13, 14, 15, 16, un, un, un, un, un, un, un, un, 
    un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, un, 
    un, 10, 11, 12, 13, 14, 15, 16
];
 
var hexEncodeArray = [
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' 
];    
 
Encoder.Hex.bytes = function(s) {
    if (s.length % 2 != 0) s = "0" + s; 
    var res = new Uint8Array(Encoder.Hex.calcNumBytes(s));
    var pos = 0;
    var inTwo = 0;
    var accum = 0;
    for(var i = 0; i < s.length; i++) {
        var code = hexDecodeArray[s.charCodeAt(i)];
        if(code===undefined) continue;
        if(inTwo===0) {
            accum = code << 4;
        } else {
            accum |= code;
            res[pos++] = accum;
        }
        inTwo = (inTwo + 1) % 2;
    }
    return res.subarray(0,pos);
};

Encoder.Hex.calcNumBytes = function(s) {
    return Math.floor(s.length/2);
};
    
Encoder.Hex.string = function(arr) {
    var s = '';
    for(var i = 0; i < arr.length; i++) {
        var code = arr[i];
        s += hexEncodeArray[code >>> 4];
        s += hexEncodeArray[code & 0x0F];
    }
    return s;
};

/*end*/})();