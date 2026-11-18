# Services result languages

Services → Preferred, Required, and Excluded languages cover the playback language
list, plus the existing Latino, Multi, and Unknown entries. These are local result
filters shared by supported resolver services; no TorBox or Nuvio backend API
change is needed. Audio-track selection remains a separate playback setting.

The previous Services enum omitted languages already available in playback.
Keep parity with playback when adding languages, reuse its localized labels, and
preserve serialized enum names. DebridStreamPresentationTest checks parity,
structured metadata, persistence, filtering, and preferred ordering.

Structured language metadata uses playback code normalization. Release-name
fallback uses explicit aliases or full language names for newly supported
languages, avoiding ambiguous short codes. Russian and Lithuanian additionally
recognize RUS/русский and LIT/lietuvių. Match Unicode token boundaries and retain
media-title removal (the existing stream presentation implementation is the
reference); never interpret MULTI as proof of a specific language.

Required languages accept any selected recognized language and hide unrecognized
results. Preferred languages affect ranking when language sorting is enabled.
Excluded languages preserve the existing multilingual behavior: hide a result
only when all its recognized languages are excluded. Metadata and release names
are hints, not verification of embedded audio tracks.
