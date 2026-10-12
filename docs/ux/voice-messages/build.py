# Copyright 2026 LMNOPhone contributors
# SPDX-License-Identifier: Apache-2.0
"""Builds voice-messages.html (all states) and renders PNGs with Playwright.

Run: python3 -I docs/ux/voice-messages/build.py   (from the repo root)
Synthetic people only: Sam (a drawn, made-up picture) and Jo (a letter).
"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
FONT = "../mockups/fonts/Lexend[wght].ttf"

# ---- symbols (inline SVG, currentColor) ----
SYM = {
    "record": '<svg viewBox="0 0 28 28" width="28" height="28"><circle cx="14" cy="14" r="12" fill="currentColor"/></svg>',
    "stop": '<svg viewBox="0 0 24 24" width="24" height="24"><rect x="2" y="2" width="20" height="20" rx="2" fill="currentColor"/></svg>',
    "play": '<svg viewBox="0 0 28 28" width="28" height="28"><path d="M6 3 L25 14 L6 25 Z" fill="currentColor"/></svg>',
    "send": '<svg viewBox="0 0 28 28" width="28" height="28"><circle cx="14" cy="14" r="12" fill="none" stroke="currentColor" stroke-width="2.5"/><path d="M8 14 H19 M14.5 9 L19.5 14 L14.5 19" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    "delete": '<svg viewBox="0 0 24 24" width="24" height="24"><path d="M4 4 L20 20 M20 4 L4 20" stroke="currentColor" stroke-width="3" stroke-linecap="round"/></svg>',
    "family": '<svg viewBox="0 0 40 30" width="40" height="30"><circle cx="13" cy="9" r="5.5" fill="none" stroke="currentColor" stroke-width="2.5"/><path d="M3 28 C3 20 8 17 13 17 C18 17 23 20 23 28" fill="none" stroke="currentColor" stroke-width="2.5"/><circle cx="28" cy="10" r="4.5" fill="none" stroke="currentColor" stroke-width="2.5"/><path d="M21 19 C23 17.5 25 17 28 17 C33 17 37 20 37 28" fill="none" stroke="currentColor" stroke-width="2.5"/></svg>',
}


def pic(who, size):
    """Synthetic picture: Sam = drawn made-up portrait (greyscale), Jo = letter."""
    if who == "Sam":
        return (f'<svg class="pic" viewBox="0 0 120 120" width="{size}" height="{size}">'
                '<defs><clipPath id="c{0}"><circle cx="60" cy="60" r="60"/></clipPath></defs>'
                '<g clip-path="url(#c{0})"><rect width="120" height="120" fill="#C9C9C9"/>'
                '<rect y="80" width="120" height="40" fill="#B4B4B4"/>'
                '<path d="M18 124 C20 92 40 82 60 82 C80 82 100 92 102 124 Z" fill="#3A3A3A"/>'
                '<rect x="51" y="66" width="18" height="20" fill="#8E8E8E"/>'
                '<ellipse cx="60" cy="52" rx="21" ry="25" fill="#9E9E9E"/>'
                '<path d="M38 50 C38 28 50 22 61 22 C74 22 84 30 82 50 C78 40 70 36 60 37 C50 37 42 41 38 50 Z" fill="#2B2B2B"/>'
                '</g></svg>').replace("{0}", str(size))
    letter = who[0]
    return (f'<div class="pic letter" style="width:{size}px;height:{size}px;font-size:{int(size*0.5)}px">'
            f'{letter}</div>')


def btn(kind, sym, label, extra=""):
    s = f'<span class="sym">{SYM[sym]}</span>' if sym else ""
    return f'<div class="btn {kind} {extra}">{s}<span>{label}</span></div>'


def phone(content, band=None, band_kind="", large=False):
    b = f'<div class="band {band_kind}">{band}</div>' if band else ""
    cls = "phone large" if large else "phone"
    return (f'<div class="{cls}"><div class="screen"><div class="status"><span>10:42</span><span>▮▮▮ ▮</span></div>'
            f'{b}<div class="content">{content}</div><div class="navbar"></div></div></div>')


def who_line(person="Sam", size=96, prefix="To "):
    return f'<div class="who">{pic(person, size)}<div class="who-name">{prefix}{person}</div></div>'


def frame(fid, title, ph, says, note=""):
    s = f'<div class="says"><span>Says</span>{says}</div>' if says else '<div class="says quiet"><span>Says</span>(nothing)</div>'
    n = f"<p>{note}</p>" if note else ""
    return (f'<figure id="{fid}">{ph}<figcaption><b>{fid} · {title}</b>{s}{n}</figcaption></figure>')


F = []

# Home with the Family row
home = ('<div class="clock">10:42</div><div class="date">Monday 12 October</div><div class="grow"></div>'
        '<div class="app">Camera</div>'
        f'<div class="app fam"><span class="sym">{SYM["family"]}</span>Family</div>'
        '<div class="app">Messages</div><div class="app">Phone</div><div class="more-plain">More</div>')
F.append(frame("V0", "Home: the Family row", phone(home), None,
               "The one symbol on Home. Always directly above Messages. Every other row stays text only."))

# V1 choose person
def card(person, extra=""):
    return f'<div class="card">{pic(person, 120)}<div class="card-name">{person}</div>{extra}</div>'

new_msg = f'<div class="card-sub"><span class="sym small">{SYM["play"]}</span>New message</div>'
v1 = ('<div class="title">Who do you want to talk to?</div>'
      + card("Sam", new_msg) + card("Jo", '<div class="card-sub">Not sent yet</div>')
      + '<div class="grow"></div><div class="link">‹ Back</div>')
F.append(frame("V1", "Choose a person", phone(v1),
               "“Who do you want to talk to?” … “Sam sent you a message.”",
               "Sam has a new message; Jo has a message not sent yet. Tapping a card only opens that person."))

# V2 person screen
v2 = (f'<div class="big-who">{pic("Jo", 160)}<div class="title center">Jo</div></div><div class="grow"></div>'
      '<div class="link">‹ Back</div>' + btn("primary", "record", "Record"))
F.append(frame("V2", "Jo’s screen", phone(v2), "“Jo. Tap Record to talk to Jo.”",
               "Tapping the picture says “Jo.” again."))

# V3 recording
v3 = (who_line("Jo") + '<div class="meter-l">Your voice</div><div class="meter"><div style="width:62%"></div></div>'
      '<div class="len"><div style="width:28%"></div></div><div class="grow"></div>'
      + btn("primary", "stop", "Stop"))
F.append(frame("V3", "Recording", phone(v3, band=f'<span class="sym">{SYM["record"]}</span>Recording', band_kind="rec"),
               "Before: “Talk to Jo after the beep.” Then the start tone. Then the microphone opens. Nothing is said while recording.",
               "Thick bar: moves with the voice. Thin line: fills as the message gets longer (no numbers). Stops on Back, Home, a call, screen off."))

# V4 review
def review(person="Jo", title=None, practice=False):
    t = f'<div class="title">{title}</div>' if title else ""
    if practice:
        return (who_line(person) + t + '<div class="grow"></div>'
                + btn("outline", "record", "Record again") + btn("outline", "delete", "Delete", "gap-big")
                + btn("primary", "play", "Listen"))
    return (who_line(person) + t + '<div class="grow"></div>'
            + btn("outline", "play", "Listen") + btn("outline", "record", "Record again")
            + btn("outline", "delete", "Delete", "gap-big") + btn("primary", "send", f"Send to {person}"))

F.append(frame("V4", "Listen, record again, delete or send", phone(review()),
               "“Listen, record again, or send to Jo.”",
               "Send is the one black button, with a gap above it, away from Delete. No extra confirmation."))

v4a = (who_line("Jo") + '<div class="title">Delete your message to Jo?</div><div class="grow"></div>'
       + btn("outline", None, "Keep it", "gap-big") + btn("primary", "delete", "Delete"))
F.append(frame("V4a", "Delete this message?", phone(v4a), "“Delete your message to Jo? Tap Delete, or Keep it.” After: “Deleted.”"))

v5 = (who_line("Jo") + '<div class="title">Sending to Jo</div><div class="grow"></div><div class="link">‹ Back</div>')
F.append(frame("V5", "Sending", phone(v5), "“Sending to Jo.”", "Leaving doesn’t stop the send."))

v6 = (who_line("Jo") + '<div class="title">Sent to Jo ✓</div><div class="grow"></div>' + btn("primary", None, "Done"))
F.append(frame("V6", "Sent", phone(v6), "Sent tone, then “Sent to Jo.”",
               "“Sent” = the phone network accepted it. Never “delivered” or “heard”."))

v7 = (who_line("Jo") + '<div class="title">Not sent to Jo</div><div class="body">Your message is still on this phone.</div>'
      '<div class="grow"></div><div class="link">‹ Back</div>' + btn("outline", "play", "Listen", "gap-big")
      + btn("primary", "send", "Try again"))
F.append(frame("V7", "Not sent", phone(v7), "Not-sent tone, then “The message didn’t send. It’s still on the phone. Tap Try again.”",
               "Never retried by itself."))

v8 = (who_line("Jo") + '<div class="title">No phone signal</div><div class="body">Your message is kept on this phone. Try again when there is signal.</div>'
      '<div class="grow"></div><div class="link">‹ Back</div>' + btn("primary", "send", "Try again"))
F.append(frame("V8", "No phone signal", phone(v8), "“There’s no phone signal. Your message is kept. Try again later.”",
               "Not sent when signal returns (Question 2)."))

F.append(frame("V9", "A message you didn’t send", phone(review(title="Your message to Jo isn’t sent yet")),
               "“Your message to Jo isn’t sent yet. Listen, send, or delete.”",
               "After Back, a call, screen off, no signal or not sent. Erased after 7 days."))

v10a = ('<div class="title">The microphone is turned off</div><div class="body">Ask Sam to turn it on. Calls and texts still work.</div>'
        '<div class="grow"></div>' + btn("primary", None, "‹ Back"))
F.append(frame("V10a", "Microphone off for the whole phone", phone(v10a), "“The microphone is turned off. Ask Sam for help.”",
               "Sam: swipe down, tap Microphone access."))
v10b = ('<div class="title">Voice messages need Sam</div><div class="body">Ask Sam to set up voice messages again. Calls and texts still work.</div>'
        '<div class="grow"></div>' + btn("primary", None, "‹ Back"))
F.append(frame("V10b", "LMNOPhone may not use the microphone", phone(v10b), "“Voice messages need Sam. Ask Sam for help.”",
               "Sam: More → For Sam → Voice messages → Turn on voice messages."))

v11 = ('<div class="android"><div class="a-sub">Android’s call screen</div><div class="a-name">Incoming call</div>'
       '<div class="a-num">555-0100</div></div>')
F.append(frame("V11", "Interrupted by a call", phone(v11), None,
               "Recording stops at once and is kept (if 1 second or longer). After the call, tapping Jo shows V9. LMNOPhone never delays a call."))

v12 = (f'<div class="big-who">{pic("Sam", 160)}<div class="title center">Sam</div></div><div class="grow"></div>'
       + btn("outline", "stop", "Stop listening") + '<div class="link">‹ Back</div>' + btn("primary", "record", "Record"))
F.append(frame("V12a", "Listening to Sam", phone(v12), "“Sam’s message.” Then the message plays."))
v12b = (f'<div class="big-who">{pic("Sam", 160)}<div class="title center">Sam</div></div><div class="grow"></div>'
        + btn("outline", "play", "Play again") + '<div class="link">‹ Back</div>' + btn("primary", "record", "Record"))
F.append(frame("V12b", "After Sam’s message", phone(v12b), "“Tap Play again to hear it again, or Record to talk to Sam.”",
               "Replying is one tap: Record."))

pv1 = ('<div class="title">Who do you want to talk to?</div>' + card("Sam") + card("Jo")
       + '<div class="grow"></div><div class="link">‹ Back</div>')
F.append(frame("V13", "Practice build: choose a person", phone(pv1, band="Practice – not sent"),
               "“This is practice. Messages are not sent.” “Who do you want to talk to?”"))
F.append(frame("V13b", "Practice build: review (no Send)", phone(review(practice=True), band="Practice – not sent"),
               "“Listen, or record again.”",
               "No Send button anywhere in the practice build."))

vt1 = ('<div class="title">Voice messages for Alex</div>'
       f'<div class="row-tp">{pic("Sam", 56)}<span>Sam</span><span class="chev">›</span></div>'
       f'<div class="row-tp">{pic("Jo", 56)}<span>Jo</span><span class="chev">›</span></div>'
       '<div class="row-tp plain"><span>Add a person</span><span class="chev">›</span></div>'
       '<div class="grow"></div><div class="link">Turn off voice messages</div>')
F.append(frame("VT1", "Trusted person: the family list", phone(vt1, band="For Sam"), None,
               "In trusted-person mode, with the PIN. No list of messages: Sam can’t see or hear them."))

v2l = (f'<div class="big-who">{pic("Jo", 120)}<div class="title center">Jo</div></div><div class="grow"></div>'
       '<div class="link">‹ Back</div>' + btn("primary", "record", "Record"))
F.append(frame("V2 · 200%", "Jo’s screen at the largest text size", phone(v2l, large=True), "“Jo. Tap Record to talk to Jo.”",
               "Illustrative, using Android 15’s 200% sizes. Words grow; pictures and symbols stay put."))

CSS = """
@font-face { font-family: "Lexend"; src: url("%s") format("truetype"); font-weight: 100 900; }
:root { --ink:#000; --paper:#FFF; --ink-2:#545454; --ink-3:#757575; --line:#AFAFAF; --fill-2:#E8E8E8; --fill-1:#F6F6F6; }
body { margin:0; background:#FFF; font-family:"Lexend",sans-serif; color:var(--ink); }
header { padding:32px 40px 8px; max-width:1100px; }
header h1 { font-weight:500; font-size:30px; margin:0 0 8px; }
header p { font-size:16px; color:var(--ink-2); line-height:1.45; margin:4px 0; }
.grid { display:flex; flex-wrap:wrap; gap:36px; padding:24px 40px 48px; }
figure { margin:0; width:460px; }
figcaption { font-size:15px; color:var(--ink-2); line-height:1.45; margin-top:12px; }
figcaption b { color:var(--ink); font-weight:500; font-size:17px; display:block; margin-bottom:6px; }
figcaption p { margin:6px 0 0; }
.says { background:var(--fill-1); border-radius:12px; padding:10px 12px; color:var(--ink); }
.says span { display:inline-block; font-weight:500; margin-right:8px; }
.says.quiet { color:var(--ink-2); }
.phone { width:436px; height:939px; border-radius:44px; border:12px solid #111; background:#111; position:relative; overflow:hidden; }
.screen { position:absolute; inset:0; background:var(--paper); border-radius:32px; overflow:hidden; display:flex; flex-direction:column; }
.status { height:32px; flex:none; display:flex; justify-content:space-between; align-items:center; padding:0 24px; font-size:13px; font-weight:500; }
.navbar { height:24px; flex:none; display:flex; justify-content:center; align-items:center; }
.navbar::after { content:""; width:108px; height:4px; border-radius:2px; background:var(--ink); opacity:.85; }
.content { flex:1; display:flex; flex-direction:column; padding:0 24px 16px; min-height:0; }
.grow { flex:1; }
.band { background:var(--ink); color:var(--paper); height:40px; flex:none; display:flex; align-items:center; padding:0 24px; font-size:16px; font-weight:500; }
.band.rec { height:56px; font-size:22px; gap:12px; }
.band .sym svg { width:22px; height:22px; display:block; }
.clock { font-size:64px; line-height:72px; margin-top:24px; letter-spacing:-1px; }
.date { font-size:18px; line-height:24px; color:var(--ink-2); }
.app { font-size:32px; font-weight:500; line-height:40px; min-height:56px; display:flex; align-items:center; }
.app.fam .sym { margin-right:14px; display:flex; }
.more-plain { text-align:center; font-size:22px; font-weight:500; padding:18px 0 4px; }
.title { font-size:28px; line-height:36px; font-weight:500; margin:24px 0 16px; }
.title.center { text-align:center; margin:16px 0 0; }
.body { font-size:18px; line-height:26px; }
.link { text-align:center; font-size:18px; font-weight:500; text-decoration:underline; min-height:48px; display:flex; align-items:center; justify-content:center; margin-bottom:8px; }
.btn { border-radius:999px; min-height:72px; display:flex; align-items:center; justify-content:center; gap:12px; font-size:20px; font-weight:500; padding:0 24px; margin-top:16px; }
.btn .sym { display:flex; }
.btn.primary { background:var(--ink); color:var(--paper); }
.btn.outline { border:1.5px solid var(--ink-3); background:var(--paper); }
.btn.gap-big { margin-bottom:16px; }
.pic { display:block; border-radius:50%%; flex:none; }
.pic.letter { background:var(--ink); color:var(--paper); display:flex; align-items:center; justify-content:center; font-weight:500; }
.card { display:flex; align-items:center; gap:20px; border:1.5px solid var(--ink-3); border-radius:16px; padding:16px; margin-bottom:16px; flex-wrap:wrap; }
.card-name { font-size:32px; font-weight:500; }
.card-sub { width:100%%; font-size:18px; display:flex; align-items:center; gap:10px; margin-top:-4px; }
.sym.small svg { width:20px; height:20px; display:block; }
.big-who { display:flex; flex-direction:column; align-items:center; margin-top:48px; }
.who { display:flex; align-items:center; gap:16px; margin-top:24px; }
.who-name { font-size:28px; font-weight:500; }
.meter-l { font-size:16px; color:var(--ink-2); margin-top:40px; }
.meter { height:16px; border-radius:999px; background:var(--fill-2); margin-top:8px; overflow:hidden; }
.meter div { height:100%%; background:var(--ink); border-radius:999px; }
.len { height:6px; background:var(--fill-2); margin-top:28px; border-radius:3px; overflow:hidden; }
.len div { height:100%%; background:var(--ink); }
.android { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; background:#EDEDED; margin:0 -24px -16px; color:#333; }
.a-sub { font-size:14px; color:#666; margin-bottom:12px; } .a-name { font-size:30px; } .a-num { font-size:20px; margin-top:8px; }
.row-tp { display:flex; align-items:center; gap:16px; min-height:72px; border-bottom:1px solid var(--fill-2); font-size:22px; font-weight:500; }
.row-tp span:nth-of-type(1) { flex:1; } .row-tp.plain span:first-child { flex:1; } .chev { color:var(--ink-3); font-weight:400; font-size:26px; }
/* 200%%: Android 15 table: 28sp->37, 18->30, 20->34, 32->40 */
.phone.large .title { font-size:37px; line-height:43px; }
.phone.large .btn { font-size:34px; min-height:96px; }
.phone.large .link { font-size:30px; }
""" % FONT

HTML = ("<!doctype html><html lang=en><meta charset=utf-8><title>LMNOPhone voice messages: states</title>"
        f"<style>{CSS}</style><header><h1>Voice messages for a person who doesn’t read: every state</h1>"
        "<p>PLA-456. Words and spoken text from docs/ux/flows-voice-messages.md. Black, white and grey; Lexend. "
        "Pictures and symbols only under the voice-message exception (docs/ux/README.md).</p>"
        "<p>Sample people only: Alex uses the phone, Sam is the trusted person, Sam and Jo are on the family list. "
        "Sam’s picture is a made-up drawing; Jo has no photo, so a letter shows. 555-0100 is a fake number.</p>"
        "<p>Browser mockups, not proof of layout on Android. The build’s screenshot tests are the measure.</p></header>"
        f'<div class="grid">{"".join(F)}</div></html>')


def main():
    out = HERE / "voice-messages.html"
    out.write_text(HTML, encoding="utf-8")
    from playwright.sync_api import sync_playwright
    png = HERE / "png"
    png.mkdir(exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1540, "height": 1000})
        pg.goto(out.as_uri())
        pg.evaluate("document.fonts.ready")
        pg.wait_for_timeout(300)
        fam = pg.evaluate("getComputedStyle(document.querySelector('.title')).fontFamily")
        print("font:", fam, "loaded:", pg.evaluate("document.fonts.check('16px Lexend')"))
        pg.screenshot(path=str(HERE / "voice-messages-contact-sheet.png"), full_page=True)
        for fig in pg.query_selector_all("figure"):
            fid = fig.get_attribute("id").replace(" · ", "-").replace("%", "pct")
            fig.screenshot(path=str(png / f"{fid}.png"))
        b.close()
    print("ok", len(F), "frames")


if __name__ == "__main__":
    sys.exit(main())
